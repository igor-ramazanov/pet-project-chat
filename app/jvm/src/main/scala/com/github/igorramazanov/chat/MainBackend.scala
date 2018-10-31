package com.github.igorramazanov.chat

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.{FromRequestUnmarshaller, Unmarshaller}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import cats.effect.Effect
import com.github.igorramazanov.chat.Utils.ExecuteToFuture
import com.github.igorramazanov.chat.Utils.ExecuteToFuture.ops._
import com.github.igorramazanov.chat.UtilsShared._
import com.github.igorramazanov.chat.api.UserApiToKvStoreApiInterpreter._
import com.github.igorramazanov.chat.api.{
  IncomingMessagesApi,
  OutgoingMessagesApi,
  PersistenceMessagesApi,
  UserApi
}
import com.github.igorramazanov.chat.domain.ChatMessage.GeneralChatMessage
import com.github.igorramazanov.chat.domain.{KeepAliveMessage, User}
import com.github.igorramazanov.chat.interpreter.redis.RedisInterpreters
import com.github.igorramazanov.chat.json.{
  DomainEntitiesJsonSupport,
  DomainEntitiesSprayJsonSupport
}
import eu.timepit.refined.types.string.NonEmptyString
import monix.eval.Task
import monix.execution.Scheduler
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

object MainBackend {

  private lazy val logger = LoggerFactory.getLogger(this.getClass)
  private val messageStrictTimeout = 1.minute
  private val shutdownTimeout = 1.minute
  private val keepAliveTimeout = 5.seconds
  private implicit val redisHost: String =
    sys.env.getOrElse("REDIS_HOST", "localhost")
  private val logLevel = sys.env.getOrElse("LOG_LEVEL", "INFO")
  sys.props.update("LOG_LEVEL", logLevel)

  def main(args: Array[String]): Unit = {
    implicit val actorSystem: ActorSystem = ActorSystem()
    implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
    implicit val scheduler: Scheduler = Scheduler(actorSystem.dispatcher)
    implicit val executeToFuture: ExecuteToFuture[Task] =
      new ExecuteToFuture[Task] {
        override def unsafeToFuture[A](f: Task[A]): Future[A] = f.runAsync
      }
    implicit val jsonSupport: DomainEntitiesJsonSupport =
      DomainEntitiesSprayJsonSupport

    import RedisInterpreters.redis
    val interpreters = InterpretersInstances[Task]
    import interpreters._

    val eventualBinding = Http().bindAndHandle(constructRoutes, "0.0.0.0", 8080)
    eventualBinding.foreach(_ =>
      logger.info("Server is listening on 8080 port"))

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
      scheduler: Scheduler,
      jsonSupport: DomainEntitiesJsonSupport,
      UserApi: UserApi[F],
      IncomingApi: IncomingMessagesApi,
      OutgoingApi: OutgoingMessagesApi[F],
      PersistenceApi: PersistenceMessagesApi[F]
  ): Route = {
    import DomainEntitiesJsonSupport._
    import jsonSupport._
    implicit val userFromRequestUnmarshaller: Unmarshaller[HttpRequest, User] =
      new FromRequestUnmarshaller[User] {
        override def apply(value: HttpRequest)(
            implicit ec: ExecutionContext,
            materializer: Materializer): Future[User] =
          value.entity
            .toStrict(messageStrictTimeout)
            .flatMap { entity =>
              entity.data.utf8String.toUser match {
                case Left(error) =>
                  Future.failed(new RuntimeException(
                    s"Couldn't unmarshall HttpRequest entity to User, entity: $entity, reason: $error"))
                case Right(user) => Future.successful(user)
              }
            }
      }
    get {
      getFromResourceDirectory("") ~
        pathSingleSlash {
          getFromResource("index.html")
        }
    } ~
      path("signin") {
        get {
          parameters(("id", "password")) { (idRaw, passwordRaw) =>
            (for {
              id <- NonEmptyString.from(idRaw)
              password <- NonEmptyString.from(passwordRaw)
            } yield {
              logger.debug(s"Sign in request start, id: '$id'")
              onComplete(UserApi.find(id.value, password.value).unsafeToFuture) {
                case Success(Some(user)) =>
                  logger.debug(s"Sign in request success, id: '$id'")
                  onComplete(createWebSocketFlow(user).unsafeToFuture) {
                    case Success(flow) =>
                      handleWebSocketMessages(flow)
                    case Failure(ex) =>
                      logger.error(
                        s"Couldn't create WebSocket flow for user '${user.id}'",
                        ex)
                      complete(StatusCodes.InternalServerError)
                  }
                case Success(None) =>
                  logger.debug(s"Sign in request forbidden, id: '$id'")
                  complete(StatusCodes.Forbidden)
                case Failure(ex) =>
                  logger.error(
                    s"Couldn't check user credentials. Id - $id, error message: ${ex.getMessage}",
                    ex)
                  complete(StatusCodes.InternalServerError)
              }
            }).getOrElse {
              complete(StatusCodes.BadRequest)
            }
          }
        }
      } ~ path("signup") {
      post {
        entity(as[User]) { user =>
          (for {
            _ <- NonEmptyString.from(user.id)
            _ <- NonEmptyString.from(user.password)
          } yield {
            logger.debug(s"Sign up request start, id: '${user.id}'")
            onComplete(UserApi.save(user).unsafeToFuture) {
              case Success(Right(_)) =>
                logger.debug(s"Sign up request success, id: '${user.id}'")
                complete(StatusCodes.OK)
              case Success(Left(reason)) =>
                logger.debug(
                  s"Sign up request forbidden: $reason, id: '${user.id}'")
                complete(StatusCodes.Conflict)
              case Failure(ex) =>
                logger.error(
                  s"Couldn't save user '${user.id}'. Error message: ${ex.getMessage}",
                  ex)
                complete(StatusCodes.InternalServerError)
            }
          }).getOrElse {
            complete(StatusCodes.BadRequest)
          }
        }
      }
    } ~ path("status") {
      complete(StatusCodes.OK)
    }
  }

  def createWebSocketFlow[F[_]: Effect: ExecuteToFuture](
      user: User
  )(implicit materializer: ActorMaterializer,
    jsonSupport: DomainEntitiesJsonSupport,
    IncomingApi: IncomingMessagesApi,
    OutgoingApi: OutgoingMessagesApi[F],
    PersistenceApi: PersistenceMessagesApi[F])
    : F[Flow[Message, Message, NotUsed]] = {
    import DomainEntitiesJsonSupport._
    import jsonSupport._

    val source = Effect[F].map(PersistenceApi.ofUserOrdered(user.id)) {
      messages =>
        val sourcePersistent =
          Source(messages).map(m => TextMessage(m.toJson))
        val sourceFlow = Source
          .fromPublisher(
            IncomingApi
              .subscribe(user.id))
          .map(m => TextMessage(m.toJson))

        sourcePersistent
          .concat(sourceFlow)
          .keepAlive(keepAliveTimeout,
                     () => TextMessage(KeepAliveMessage.Pong.toString))
          .map { m =>
            logger.debug(s"Outgoing to user '${user.id}': $m")
            m
          }
    }

    val saveAndPublish: GeneralChatMessage => F[Unit] = {
      import cats.syntax.all._
      m: GeneralChatMessage =>
        for {
          _ <- PersistenceApi.save(m.from, m)
          _ <- PersistenceApi.save(m.to, m)
          _ <- OutgoingApi.send(m)
        } yield ()
    }

    val sink = Flow[Message]
      .map { m =>
        logger.debug(s"Incoming from user '${user.id}': $m")
        m
      }
      .mapAsync(Runtime.getRuntime.availableProcessors())(
        _.asTextMessage.asScala.toStrict(messageStrictTimeout))
      .map(_.text)
      .filter(_ != "ping")
      .mapConcat { jsonString =>
        jsonString.toIncomingMessage.fold(
          { error =>
            logger.warn(
              s"Couldn't parse incoming websocket message: $jsonString to IncomingChatMessage, reason: $error")
            Nil
          },
          m => List(m.asGeneral(user, Utils.currentUtcUnixEpochMillis))
        )
      }
      .to(Sink.foreach[GeneralChatMessage] { m =>
        saveAndPublish(m).unsafeToFuture.discard()
      })

    Effect[F].map(source) { s =>
      Flow.fromSinkAndSource(sink, s)
    }
  }
}
