package com.github.igorramazanov.chat.route

import akka.NotUsed
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import cats.Monad
import com.github.igorramazanov.chat.Utils.ExecuteToFuture
import com.github.igorramazanov.chat.Utils.ExecuteToFuture.ops._
import com.github.igorramazanov.chat.UtilsShared._
import com.github.igorramazanov.chat.api._
import com.github.igorramazanov.chat.domain.ChatMessage.GeneralChatMessage
import com.github.igorramazanov.chat.domain.{
  KeepAliveMessage,
  SignUpOrInRequest,
  User
}
import com.github.igorramazanov.chat.json.DomainEntitiesJsonSupport
import com.github.igorramazanov.chat.{ResponseCode, Utils}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object SignIn extends AbstractRoute {
  private val logger = LoggerFactory.getLogger(getClass)
  private val messageStrictTimeout = 1.minute
  private val keepAliveTimeout = 5.seconds

  def createRoute[
      F[_]: UserApi: ExecuteToFuture: Monad: OutgoingMessagesApi: PersistenceMessagesApi](
      implicit materializer: ActorMaterializer,
      jsonSupport: DomainEntitiesJsonSupport,
      IncomingApi: IncomingMessagesApi): Route = path("signin") {
    import jsonSupport._
    import DomainEntitiesJsonSupport._
    get {
      parameters(("id", "email", "password")) {
        (idRaw, emailRaw, passwordRaw) =>
          SignUpOrInRequest(idRaw, passwordRaw, emailRaw).validate.fold(
            invalidRequest =>
              complete(
                HttpResponse(
                  status = StatusCode.int2StatusCode(
                    ResponseCode.ValidationErrors.value),
                  entity = HttpEntity(MediaTypes.`application/json`,
                                      invalidRequest.toJson))), { validRequest =>
              logger.debug(s"Sign in request start, ${validRequest.toString}")
              onComplete(
                UserApi[F]
                  .`match`(validRequest.id,
                           validRequest.email,
                           validRequest.password)
                  .unsafeToFuture) {
                case Success(Some(user)) =>
                  logger.debug(
                    s"Sign in request success, ${validRequest.toString}")
                  onComplete(createWebSocketFlow(user).unsafeToFuture) {
                    case Success(flow) =>
                      handleWebSocketMessages(flow)
                    case Failure(ex) =>
                      logger.error(
                        s"Couldn't create WebSocket flow for request '${validRequest.toString}'",
                        ex)
                      complete(ResponseCode.ServerError)
                  }
                case Success(None) =>
                  logger.debug(
                    s"Sign in request forbidden, request: '${validRequest.toString}'")
                  complete(ResponseCode.InvalidCredentials)
                case Failure(ex) =>
                  logger.error(
                    s"Couldn't check user credentials, request - ${validRequest.toString}, error message: ${ex.getMessage}",
                    ex)
                  complete(ResponseCode.ServerError)
              }
            }
          )
      }
    }
  }

  private def createWebSocketFlow[
      F[_]: ExecuteToFuture: Monad: OutgoingMessagesApi: PersistenceMessagesApi](
      user: User
  )(implicit materializer: ActorMaterializer,
    jsonSupport: DomainEntitiesJsonSupport,
    IncomingApi: IncomingMessagesApi): F[Flow[Message, Message, NotUsed]] = {
    import DomainEntitiesJsonSupport._
    import jsonSupport._

    val source =
      Monad[F].map(PersistenceMessagesApi[F].ofUserOrdered(user.id)) {
        messages =>
          val sourcePersistent =
            Source(messages).map(m => TextMessage(m.toJson))
          val sourceFlow = Source
            .fromPublisher(
              IncomingApi
                .subscribe(user.id.value))
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
          _ <- PersistenceMessagesApi[F].save(m.from, m)
          _ <- PersistenceMessagesApi[F].save(m.to, m)
          _ <- OutgoingMessagesApi[F].send(m)
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
          m => List(m.asGeneral(user.id, Utils.currentUtcUnixEpochMillis))
        )
      }
      .to(Sink.foreach[GeneralChatMessage] { m =>
        saveAndPublish(m).unsafeToFuture.discard()
      })

    Monad[F].map(source) { s =>
      Flow.fromSinkAndSource(sink, s)
    }
  }
}
