package com.github.igorramazanov.chat.components
import com.github.igorramazanov.chat.ResponseCode
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

  sealed trait Page extends Product with Serializable
  object Page {
    final case object Welcoming extends Page
    final case object Chat      extends Page
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

  final case class State(
      user: Option[User],
      currentPage: Page,
      isInFlight: Boolean,
      ws: Option[WebSocket],
      messages: Map[User.Id, List[GeneralChatMessage]],
      alerts: List[Alert]
  ) {
    def appendMessage(id: User.Id, message: GeneralChatMessage): State = {
      val interlocutor     =
        if (id == message.from) message.to else message.from
      val previousMessages = messages.getOrElse(interlocutor, Nil)
      val withNewMessage   = previousMessages ::: (message :: Nil)
      copy(messages = messages.updated(interlocutor, withNewMessage))
    }

    def addNewContact(username: User.Id): State =
      copy(messages = messages + (username -> Nil))

    def addAlert(alert: Alert): State           = copy(alerts = alert :: alerts)

    def removeAlert(alertId: String): State =
      copy(alerts = alerts.filterNot(_.id == alertId))
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

  final class Backend(
      $ : BackendScope[Unit, State],
      jsonSupport: DomainEntitiesJsonSupport
  ) {
    import DomainEntitiesJsonSupport._
    import jsonSupport._
    private def signIn(
        id: User.Id,
        email: User.Email,
        password: User.Password
    ): Callback = {
      def schedulePings(s: State): Unit =
        js.timers
          .setInterval(5.seconds)(
            s.ws.foreach(_.send(KeepAliveMessage.Ping.toString))
          )
          .discard()

      def connect =
        CallbackTo {
          val host   =
            org.scalajs.dom.window.location.hostname
          val port   = org.scalajs.dom.window.location.port
          val url    =
            s"ws://$host:$port/signin?id=${id.value}&email=${email.value}&password=${password.value}"
          val direct = $.withEffectsImpure

          @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
          def onopen(e: Event): Unit =
            direct.modState { s =>
              schedulePings(s)
              s.copy(
                user = Some(User.safeCreate(id, password, email)),
                currentPage = Page.Chat,
                isInFlight = false
              )
            }

          def onmessage(e: MessageEvent): Unit = {
            val rawMessage = e.data.toString
            if (rawMessage != KeepAliveMessage.Pong.toString) {
              val messageEither = rawMessage.toGeneralMessage

              messageEither match {
                case Left(error)    =>
                  direct.modState(
                    _.addAlert(
                      Alert(
                        s"Couldn't parse message '${messageEither.toString}' to show, reason: ${error.toString}",
                        Alert.Type.Warning
                      )
                    )
                  )
                case Right(message) =>
                  direct.modState(_.appendMessage(id, message))
              }
            }
          }

          def onerror(e: Event): Unit =
            direct.modState(_.copy(isInFlight = false))

          def onclose(e: CloseEvent): Unit =
            direct.modState(
              _.addAlert(
                Alert("Web socket connection failure.", Alert.Type.Failure)
              ).copy(isInFlight = false)
            )

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

    private def signUp(
        id: User.Id,
        email: User.Email,
        password: User.Password
    ): Callback =
      $.modState(_.copy(isInFlight = true)) >>
        Ajax("POST", "/signup").setRequestContentTypeJsonUtf8
          .send(
            User.safeCreate(id, password, email).toJson
          )
          .onComplete { xhr =>
            $.modState(_.copy(isInFlight = false)) >> {
              xhr.status match {
                case ResponseCode.Ok.value                                =>
                  signIn(id, email, password)
                case ResponseCode.SuccessfullySentVerificationEmail.value =>
                  $.modState(
                    _.addAlert(
                      Alert(
                        s"Successfully sent verification email on ${email.value}",
                        Alert.Type.Success
                      )
                    )
                  )
                case ResponseCode.UserAlreadyExists.value                 =>
                  $.modState(
                    _.addAlert(
                      Alert(
                        s"User with id '${id.value}' already exists",
                        Alert.Type.Warning
                      )
                    )
                  )
                case ResponseCode.ServerError.value                       =>
                  $.modState(
                    _.addAlert(
                      Alert(
                        s"Couldn't sign up - server error",
                        Alert.Type.Failure
                      )
                    )
                  )
                case other                                                =>
                  $.modState(
                    _.addAlert(
                      Alert(
                        s"Server returned code ${other.toString} which is not handled by the client app",
                        Alert.Type.Warning
                      )
                    )
                  )
              }
            }
          }
          .asCallback

    private def addNewContact(contact: User.Id): Callback =
      Ajax(
        "GET",
        s"/exists?id=${contact.value}"
      ).setRequestContentTypeJsonUtf8.send.onComplete { xhr =>
        xhr.status match {
          case ResponseCode.Ok.value                => $.modState(_.addNewContact(contact))
          case ResponseCode.UserDoesNotExists.value =>
            $.modState(
              _.addAlert(
                Alert(
                  s"The user '${contact.value}' does not exists",
                  Alert.Type.Warning
                )
              )
            )
          case ResponseCode.ServerError.value       =>
            $.modState(
              _.addAlert(
                Alert(
                  s"Couldn't check existence of user ${contact.value}, server error",
                  Alert.Type.Failure
                )
              )
            )
          case otherwise                            =>
            $.modState(
              _.addAlert(
                Alert(
                  s"Couldn't check existence of user ${contact.value}, server returned ${otherwise.toString}",
                  Alert.Type.Failure
                )
              )
            )
        }
      }.asCallback

    private def send(to: User.Id, message: String): Callback =
      $.state.map { s =>
        for {
          _   <- s.user
          ws  <- s.ws
          json = IncomingChatMessage(to, message).toJson
        } yield ws.send(json)
      }.void

    def render(s: State): VdomElement =
      <.div(
        s.currentPage match {
          case Page.Welcoming =>
            WelcomeComponent.Component(
              WelcomeComponent.Props(signIn, signUp, s.isInFlight)
            )
          case Page.Chat      =>
            ChatComponent.Component(
              ChatComponent.Props(s.user, s.messages, addNewContact, send)
            )
        },
        AlertsComponent.Component(
          AlertsComponent.Props(s.alerts, id => $.modState(_.removeAlert(id)))
        )
      )

  }

  def Component(jsonSupport: DomainEntitiesJsonSupport) =
    ScalaComponent
      .builder[Unit]("MainComponent")
      .initialState(State.init)
      .backend(p => new Backend(p, jsonSupport))
      .renderBackend
      .build
}
