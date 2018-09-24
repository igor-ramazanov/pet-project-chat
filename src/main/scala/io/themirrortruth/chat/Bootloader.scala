package io.themirrortruth.chat
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import cats.effect.Effect
import io.themirrortruth.chat.Utils.ExecuteToFuture.ops._
import io.themirrortruth.chat.Utils.{AnyOps, ExecuteToFuture}
import io.themirrortruth.chat.api.UserApiToKvStoreApiInterpreter._
import io.themirrortruth.chat.api.{
  IncomingMessagesApi,
  OutgoingMesssagesApi,
  PersistenceMessagesApi,
  UserApi
}
import io.themirrortruth.chat.entity.ChatMessage._
import io.themirrortruth.chat.entity.User
import io.themirrortruth.chat.interpreter.redis.RedisInterpreters
import monix.eval.Task
import monix.execution.Scheduler
import org.slf4j.LoggerFactory
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

object Bootloader {
  private val logger = LoggerFactory.getLogger(this.getClass)
  private val messageStrictTimeout = 1.minute
  private val shutdownTimeout = 1.minute
  private val messagesStrictParallelism = 1
  private implicit val redisHost: String =
    sys.env.getOrElse("REDIS_HOST", "localhost")

  def main(args: Array[String]): Unit = {
    implicit val actorSystem: ActorSystem = ActorSystem()
    implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
    implicit val scheduler: Scheduler = Scheduler(actorSystem.dispatcher)
    implicit val executeToFuture: ExecuteToFuture[Task] =
      new ExecuteToFuture[Task] {
        override def unsafeToFuture[A](f: Task[A]): Future[A] = f.runAsync
      }

    import RedisInterpreters.redis
    val interpreters = InterpretersInstances[Task]
    import interpreters._

    val eventualBinding = Http().bindAndHandle(constructRoutes, "0.0.0.0", 8080)

    sys
      .addShutdownHook {
        Await
          .result(eventualBinding.flatMap(_.unbind()), shutdownTimeout)
          .discard()
      }
      .discard()
  }

  def constructRoutes[F[_]: ExecuteToFuture: Effect](
      implicit materializer: ActorMaterializer,
      UserApi: UserApi[F],
      IncomingApi: IncomingMessagesApi,
      OutgoingApi: OutgoingMesssagesApi[F],
      PersistenceApi: PersistenceMessagesApi[F]
  ): Route = {
    path("signin") {
      entity(as[User]) { user =>
        onComplete(UserApi.find(user.id, user.password).unsafeToFuture) {
          case Success(Some(_)) =>
            handleWebSocketMessages(createWebSocketFlow(user))
          case Success(None) =>
            complete(StatusCodes.Forbidden)
          case Failure(ex) =>
            logger.error(
              s"Couldn't check user credentials. Id - ${user.id}, error message: ${ex.getMessage}",
              ex)
            complete(StatusCodes.InternalServerError)
        }
      }
    } ~ path("signup") {
      entity(as[User]) { user =>
        onComplete(UserApi.save(user).unsafeToFuture) {
          case Success(Right(_)) =>
            complete(StatusCodes.OK)
          case Success(Left(_)) =>
            complete(StatusCodes.Conflict)
          case Failure(ex) =>
            logger.error(
              s"Couldn't save user '${user.id}'. Error message: ${ex.getMessage}",
              ex)
            complete(StatusCodes.InternalServerError)
        }
      }
    }
  }

  def createWebSocketFlow[F[_]: Effect: ExecuteToFuture](
      user: User
  )(implicit materializer: ActorMaterializer,
    IncomingApi: IncomingMessagesApi,
    OutgoingApi: OutgoingMesssagesApi[F],
    PersistenceApi: PersistenceMessagesApi[F]): Flow[Message, Message, Any] = {
    val source = Source
      .fromPublisher(
        IncomingApi
          .subscribe(user))
      .map(m => TextMessage(m.asOutgoing.toJson.compactPrint))

    val saveAndPublish: GeneralChatMessage => F[Unit] = {
      m: GeneralChatMessage =>
        val save = PersistenceApi.save(user, m)
        val publish = OutgoingApi.send(m)
        Effect[F].flatMap(save)(_ => publish)
    }

    val sink = Flow[Message]
      .mapAsync(messagesStrictParallelism)(
        _.asTextMessage.asScala.toStrict(messageStrictTimeout))
      .map(
        _.text.parseJson
          .convertTo[IncomingChatMessage]
          .asGeneral(user))
      .to(Sink.foldAsync(())((_, m) => saveAndPublish(m).unsafeToFuture))

    Flow.fromSinkAndSourceCoupled(sink, source)
  }
}
