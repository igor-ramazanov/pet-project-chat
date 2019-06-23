package com.github.igorramazanov.chat.route

import akka.NotUsed
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.syntax.all._
import akka.stream.scaladsl.{Flow, Sink, Source}
import cats.Monad
import cats.instances.string._
import com.github.igorramazanov.chat.Utils.ToFuture
import com.github.igorramazanov.chat.Utils.ToFuture.ops._
import com.github.igorramazanov.chat.api._
import com.github.igorramazanov.chat.domain.ChatMessage.GeneralChatMessage
import com.github.igorramazanov.chat.domain.{KeepAliveMessage, SignUpOrInRequest, User}
import com.github.igorramazanov.chat.json.DomainEntitiesJsonSupport
import com.github.igorramazanov.chat.{ResponseCode, Utils}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object SignIn extends AbstractRoute {
  private val logger           = LoggerFactory.getLogger(getClass)
  private val keepAliveTimeout = 5.seconds

  def createRoute[F[_]: UserApi: ToFuture: Monad: RealtimeOutgoingMessagesApi: PersistenceMessagesApi: RealtimeIncomingMessagesApi](
      implicit
      jsonSupport: DomainEntitiesJsonSupport
  ): Route = path("signin") {
    import DomainEntitiesJsonSupport._
    import jsonSupport._
    get {
      parameters(("id", "email", "password")) { (idRaw, emailRaw, passwordRaw) =>
        SignUpOrInRequest(idRaw, passwordRaw, emailRaw).validate.fold(
          invalidRequest =>
            complete(
              HttpResponse(
                status = StatusCode.int2StatusCode(ResponseCode.ValidationErrors.value),
                entity = HttpEntity(MediaTypes.`application/json`, invalidRequest.toJson)
              )
            ), { validRequest =>
            logger.debug(s"Sign in request start, ${validRequest.toString}")
            onComplete(
              UserApi[F]
                .`match`(validRequest.id, validRequest.email, validRequest.password)
                .unsafeToFuture
            ) {
              case Success(Some(user)) =>
                logger.debug(s"Sign in request success, ${validRequest.toString}")
                onComplete(createWebSocketFlow(user).unsafeToFuture) {
                  case Success(flow) =>
                    handleWebSocketMessages(flow)
                  case Failure(ex) =>
                    logger.error(
                      s"Couldn't create WebSocket flow for request '${validRequest.toString}'",
                      ex
                    )
                    complete(ResponseCode.ServerError)
                }
              case Success(None) =>
                logger.debug(s"Sign in request forbidden, request: '${validRequest.toString}'")
                complete(ResponseCode.InvalidCredentials)
              case Failure(ex) =>
                logger.error(
                  s"Couldn't check user credentials, request - ${validRequest.toString}, error message: ${ex.getMessage}",
                  ex
                )
                complete(ResponseCode.ServerError)
            }
          }
        )
      }
    }
  }

  private def createWebSocketFlow[F[_]: Monad: RealtimeOutgoingMessagesApi: PersistenceMessagesApi: ToFuture: RealtimeIncomingMessagesApi](
      user: User
  )(
      implicit
      jsonSupport: DomainEntitiesJsonSupport
  ): F[Flow[Message, Message, NotUsed]] = {
    import DomainEntitiesJsonSupport._
    import jsonSupport._

    val sourceEffect =
      for {
        persistenceMessagesPublisher <- PersistenceMessagesApi[F]
                                         .ofUserOrdered(user.id)
        realtimeIncomingMessagesPublisher <- RealtimeIncomingMessagesApi[F]
                                              .subscribe(user.id)
      } yield {
        Source
          .fromPublisher(persistenceMessagesPublisher)
          .concat(Source.fromPublisher(realtimeIncomingMessagesPublisher))
          .map(m => TextMessage(m.toJson))
          .keepAlive(keepAliveTimeout, () => TextMessage(KeepAliveMessage.Pong.toString))
          .map { m =>
            logger.debug(s"Outgoing to user '${user.id}': $m")
            m
          }
      }

    val parsingFlow = Flow[Message]
      .flatMapConcat { m =>
        logger.debug(s"Incoming from user '${user.id}': $m")
        m.asTextMessage.asScala.textStream
      }
      .filterNot(KeepAliveMessage.Ping.toString === _)
      .map { jsonString =>
        jsonString.toIncomingMessage.map(
          message => message.asGeneral(user.id, Utils.currentUtcUnixEpochMillis)
        )
      }
      .flatMapConcat {
        case Left(errors) =>
          logger.warn(
            s"Couldn't parse incoming websocket message to IncomingChatMessage, reasons: ${errors
              .mkString_("", ", ", "")}"
          )
          Source.empty[GeneralChatMessage]
        case Right(m) => Source.single(m)
      }

    for {
      source                     <- sourceEffect
      persistenceSubscriber      <- PersistenceMessagesApi[F].save()
      realtimeOutgoingSubscriber <- RealtimeOutgoingMessagesApi[F].send()
    } yield Flow.fromSinkAndSource(
      parsingFlow
        .alsoTo(Sink.fromSubscriber(persistenceSubscriber))
        .to(Sink.fromSubscriber(realtimeOutgoingSubscriber)),
      source
    )
  }
}
