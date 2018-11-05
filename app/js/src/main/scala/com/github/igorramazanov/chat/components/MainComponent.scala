package com.github.igorramazanov.chat.components
import com.github.igorramazanov.chat.UtilsShared._
import com.github.igorramazanov.chat.domain.ChatMessage.{
  GeneralChatMessage,
  IncomingChatMessage
}
import com.github.igorramazanov.chat.domain.{KeepAliveMessage, User}
import com.github.igorramazanov.chat.json._
import japgolly.scalajs.react.extra.Ajax
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{
  BackendScope,
  Callback,
  CallbackTo,
  ScalaComponent
}
import org.scalajs.dom.raw.WebSocket
import org.scalajs.dom.{CloseEvent, Event, MessageEvent}

import scala.concurrent.duration._
import scala.scalajs.js
import scala.util.Random

@SuppressWarnings(Array("org.wartremover.warts.Any"))
object MainComponent {
  val UserAlreadyExists = 409

  sealed trait Page extends Product with Serializable
  object Page {
    final case object Welcoming extends Page
    final case object Chat extends Page
  }

  final case class Alert(content: String, `type`: Alert.Type) {
    val id: String = Random.alphanumeric.take(12).mkString("")
  }

  object Alert {

    sealed trait Type extends Product with Serializable {
      def className: String
    }
    object Type {
      final case object Success extends Type {
        override def className: String = "alert-success"
      }
      final case object Failure extends Type {
        override def className: String = "alert-danger"
      }
      final case object Warning extends Type {
        override def className: String = "alert-warning"
      }
    }
  }

  final case class State(user: Option[User],
                         currentPage: Page,
                         isInFlight: Boolean,
                         ws: Option[WebSocket],
                         messages: Map[String, List[GeneralChatMessage]],
                         alerts: List[Alert]) {
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

    def addAlert(alert: Alert): State = copy(alerts = alert :: alerts)

    def removeAlert(id: String): State =
      copy(alerts = alerts.filterNot(_.id == id))
  }

  object State {
    def init: State =
      State(
        None,
        Page.Welcoming,
        isInFlight = false,
        None,
        Map.empty,
        Nil
      )
  }

  final class Backend($ : BackendScope[Unit, State],
                      jsonSupport: DomainEntitiesJsonSupport) {
    import DomainEntitiesJsonSupport._
    import jsonSupport._
    private def signIn(id: User.Id,
                       email: User.Email,
                       password: User.Password): Callback = {
      def schedulePings(s: State): Unit =
        js.timers
          .setInterval(5.seconds)(
            s.ws.foreach(_.send(KeepAliveMessage.Ping.toString)))
          .discard()

      def connect = CallbackTo {
        val host =
          org.scalajs.dom.window.location.hostname
        val port = org.scalajs.dom.window.location.port
        val url =
          s"ws://$host:$port/signin?id=${id.value}&email=${email.value}&password=${password.value}"
        val direct = $.withEffectsImpure

        @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
        def onopen(e: Event): Unit = {
          direct.modState { s =>
            schedulePings(s)
            s.copy(user = Some(User.safeCreate(id, password, email)),
                   currentPage = Page.Chat,
                   isInFlight = false)
          }
        }

        def onmessage(e: MessageEvent): Unit = {
          val rawMessage = e.data.toString
          if (rawMessage != KeepAliveMessage.Pong.toString) {
            val messageEither = rawMessage.toGeneralMessage

            messageEither match {
              case Left(error) =>
                direct.modState(_.addAlert(Alert(
                  s"Couldn't parse message '$messageEither' to show, reason: $error",
                  Alert.Type.Warning)))
              case Right(message) =>
                direct.modState(_.appendMessage(id.value, message))
            }
          }
        }

        def onerror(e: Event): Unit =
          direct.modState(_.copy(isInFlight = false))

        def onclose(e: CloseEvent): Unit =
          direct.modState(
            _.addAlert(Alert("Web socket connection failure.",
                             Alert.Type.Failure)).copy(isInFlight = false))

        val ws = new WebSocket(url)
        ws.onopen = onopen
        ws.onclose = onclose
        ws.onmessage = onmessage
        ws.onerror = onerror
        ws
      }

      $.modState(_.copy(isInFlight = true)) >> connect.attempt >>= {
        case Right(ws) => $.modState(_.copy(ws = Some(ws)))
        case _         => Callback.empty
      }
    }

    private def signUp(id: User.Id,
                       email: User.Email,
                       password: User.Password): Callback = {
      $.modState(_.copy(isInFlight = true)) >>
        Ajax("POST", "/signup").setRequestContentTypeJsonUtf8
          .send(
            User.safeCreate(id, password, email).toJson
          )
          .onComplete { xhr =>
            $.modState(_.copy(isInFlight = false)) >> {
              xhr.status match {
                case 200 =>
                  $.modState(
                    _.addAlert(Alert(
                      s"Successfully sent verification email on ${email.value}",
                      Alert.Type.Success)))
                case UserAlreadyExists =>
                  $.modState(
                    _.addAlert(
                      Alert(s"User with id '${id.value}' already exists",
                            Alert.Type.Warning)))
                case other =>
                  $.modState(
                    _.addAlert(
                      Alert(s"Server returned code $other instead of 'Success'",
                            Alert.Type.Warning)))
              }
            }
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
      <.div(
        s.currentPage match {
          case Page.Welcoming =>
            WelcomeComponent.Component(
              WelcomeComponent.Props(signIn, signUp, s.isInFlight))
          case Page.Chat =>
            ChatComponent.Component(
              ChatComponent.Props(s.user, s.messages, addNewContact, send))
        },
        AlertsComponent.Component(
          AlertsComponent.Props(s.alerts, id => $.modState(_.removeAlert(id))))
      )

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
