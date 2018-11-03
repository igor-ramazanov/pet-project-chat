package com.github.igorramazanov.chat.components
import com.github.igorramazanov.chat.domain.ChatMessage.{
  GeneralChatMessage,
  IncomingChatMessage
}
import com.github.igorramazanov.chat.domain.{KeepAliveMessage, User}
import japgolly.scalajs.react.extra.Ajax
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{
  BackendScope,
  Callback,
  CallbackTo,
  ScalaComponent
}
import com.github.igorramazanov.chat.json._
import com.github.igorramazanov.chat.UtilsShared._
import org.scalajs.dom.{CloseEvent, Event, MessageEvent}
import org.scalajs.dom.raw.WebSocket

import scala.scalajs.js
import scala.concurrent.duration._

@SuppressWarnings(Array("org.wartremover.warts.Any"))
object MainComponent {
  val InvalidCredentials = 1006

  sealed trait Page extends Product with Serializable
  object Page {
    final case object Welcoming extends Page
    final case object Chat extends Page
  }

  final case class State(user: Option[User],
                         currentPage: Page,
                         isInFlight: Boolean,
                         ws: Option[WebSocket],
                         messages: Map[String, List[GeneralChatMessage]]) {
    def appendMessage(username: String, message: GeneralChatMessage): State = {
      val interlocutor =
        if (username == message.from) message.to else message.from
      val previousMessages = messages.getOrElse(interlocutor, Nil)
      val withNewMessage = previousMessages ::: (message :: Nil)
      copy(messages = messages.updated(interlocutor, withNewMessage))
    }

    def addNewContact(username: String): State = {
      copy(messages = messages + (username -> Nil))
    }
  }

  object State {
    def init: State =
      State(None, Page.Welcoming, isInFlight = false, None, Map.empty)
  }

  final class Backend($ : BackendScope[Unit, State],
                      jsonSupport: DomainEntitiesJsonSupport) {
    import jsonSupport._
    import DomainEntitiesJsonSupport._
    private def signIn(username: String, password: String): Callback = {
      def schedulePings(s: State): Unit =
        js.timers
          .setInterval(5.seconds)(
            s.ws.foreach(_.send(KeepAliveMessage.Ping.toString)))
          .discard()

      def connect = CallbackTo {
        val host =
          org.scalajs.dom.window.location.hostname
        val port = org.scalajs.dom.window.location.port
        val url = s"ws://$host:$port/signin?id=$username&password=$password"
        val direct = $.withEffectsImpure

        @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
        def onopen(e: Event): Unit = {
          direct.modState { s =>
            schedulePings(s)
            s.copy(user = Some(User.unsafeCreate(username, password)),
                   currentPage = Page.Chat)
          }
        }

        def onmessage(e: MessageEvent): Unit = {
          val rawMessage = e.data.toString
          if (rawMessage != KeepAliveMessage.Pong.toString) {
            val messageEither = rawMessage.toGeneralMessage

            messageEither match {
              case Left(error) =>
                System.err.println(
                  s"Couldn't parse message: $messageEither as GeneralChatMessage, reason: $error")
              case Right(message) =>
                direct.modState(_.appendMessage(username, message))
            }
          }
        }

        @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
        def onerror(e: Event): Unit = {
          val msg: String =
            e.asInstanceOf[js.Dynamic]
              .message
              .asInstanceOf[js.UndefOr[String]]
              .fold(s"Error occurred!")("Error occurred: " + _)
          System.err.println(msg)
        }

        def onclose(e: CloseEvent): Unit = {
          if (e.code == InvalidCredentials) {
            System.err.println(s"""Invalid credentials.""")
          } else {
            System.err.println(
              s"""Closed. Code: ${e.code}. Reason = "${e.reason}".""")
            signIn(username, password).discard()
          }
        }

        val ws = new WebSocket(url)
        ws.onopen = onopen
        ws.onclose = onclose
        ws.onmessage = onmessage
        ws.onerror = onerror
        ws
      }

      connect.attempt >>= {
        case Right(ws) => $.modState(_.copy(ws = Some(ws)))
        case _         => Callback.empty
      }
    }

    private def signUp(username: String, password: String): Callback = {
      $.modState(_.copy(isInFlight = true)) >>
        Ajax("POST", "/signup").setRequestContentTypeJsonUtf8
          .send(
            User.unsafeCreate(username, password).toJson
          )
          .onComplete { xhr =>
            $.modState(_.copy(isInFlight = false)) >> (xhr.status match {
              case 200 =>
                signIn(username, password)
              case _ =>
                Callback {
                  System.err.println(
                    s"Sign up: failure, reason: ${Ajax.deriveErrorMessage(xhr)}")
                }
            })
          }
          .asCallback
    }

    private def addNewContact(otherUser: String): Callback =
      $.modState(_.addNewContact(otherUser))

    private def send(to: String, message: String): Callback = {
      $.state.map { s =>
        for {
          _ <- s.user
          ws <- s.ws
          json = IncomingChatMessage(to, message).toJson
        } yield ws.send(json)
      }.void
    }

    def render(s: State): VdomElement = {
      s.currentPage match {
        case Page.Welcoming =>
          WelcomeComponent.Component(
            WelcomeComponent.Props(signIn, signUp, s.isInFlight))
        case Page.Chat =>
          ChatComponent.Component(
            ChatComponent.Props(s.user, s.messages, addNewContact, send))
      }
    }
  }

  def Component(jsonSupport: DomainEntitiesJsonSupport) =
    ScalaComponent
      .builder[Unit]("MainComponent")
      .initialState(State.init)
      .backend(p => new Backend(p, jsonSupport))
      .renderBackend
      .build
}
