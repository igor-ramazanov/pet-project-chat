package com.github.igorramazanov.chat.route

import akka.NotUsed
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import cats.effect.Effect
import com.github.igorramazanov.chat.Utils
import com.github.igorramazanov.chat.Utils.ExecuteToFuture
import com.github.igorramazanov.chat.Utils.ExecuteToFuture.ops._
import com.github.igorramazanov.chat.UtilsShared._
import com.github.igorramazanov.chat.api._
import com.github.igorramazanov.chat.domain.ChatMessage.GeneralChatMessage
import com.github.igorramazanov.chat.domain.{KeepAliveMessage, User}
import com.github.igorramazanov.chat.json.DomainEntitiesJsonSupport
import eu.timepit.refined.types.string.NonEmptyString
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object SignIn {
  private val logger = LoggerFactory.getLogger(getClass)
  private val messageStrictTimeout = 1.minute
  private val keepAliveTimeout = 5.seconds

  def createRoute[
      F[_]: UserApi: ExecuteToFuture: Effect: OutgoingMessagesApi: PersistenceMessagesApi](
      implicit materializer: ActorMaterializer,
      jsonSupport: DomainEntitiesJsonSupport,
      IncomingApi: IncomingMessagesApi): Route = path("signin") {
    get {
      parameters(("id", "email", "password")) {
        (idRaw, emailRaw, passwordRaw) =>
          (for {
            id <- NonEmptyString
              .from(idRaw)
              .map(s => User.Id.unsafeCreate(s.value))
            email <- NonEmptyString
              .from(emailRaw)
              .map(s => User.Email.unsafeCreate(s.value))
            password <- NonEmptyString
              .from(passwordRaw)
              .map(s => User.Password.unsafeCreate(s.value))
          } yield {
            logger.debug(s"Sign in request start, email: '$email'")
            onComplete(UserApi[F].`match`(id, email, password).unsafeToFuture) {
              case Success(Some(user)) =>
                logger.debug(s"Sign in request success, email: '$email'")
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
                logger.debug(s"Sign in request forbidden, email: '$email'")
                complete(StatusCodes.Forbidden)
              case Failure(ex) =>
                logger.error(
                  s"Couldn't check user credentials. Email - $email, error message: ${ex.getMessage}",
                  ex)
                complete(StatusCodes.InternalServerError)
            }
          }).getOrElse {
            complete(StatusCodes.BadRequest)
          }
      }
    }
  }

  private def createWebSocketFlow[
      F[_]: ExecuteToFuture: Effect: OutgoingMessagesApi: PersistenceMessagesApi](
      user: User
  )(implicit materializer: ActorMaterializer,
    jsonSupport: DomainEntitiesJsonSupport,
    IncomingApi: IncomingMessagesApi): F[Flow[Message, Message, NotUsed]] = {
    import DomainEntitiesJsonSupport._
    import jsonSupport._

    val source =
      Effect[F].map(PersistenceMessagesApi[F].ofUserOrdered(user.id.value)) {
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
