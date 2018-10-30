package com.github.igorramazanov.chat.components

import com.github.igorramazanov.chat.domain.ChatMessage.GeneralChatMessage
import com.github.igorramazanov.chat.domain.User
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

@SuppressWarnings(Array("org.wartremover.warts.Any"))
object ChatComponent {

  final case class Props(user: Option[User],
                         messages: Map[String, List[GeneralChatMessage]],
                         addUser: String => Callback,
                         send: (String, String) => Callback)

  final case class State(active: Option[String])

  object State {
    def init: State = State(None)
  }

  final class Backend($ : BackendScope[Props, State]) {

    def render(p: Props, s: State): VdomElement = {
      val discussionMessages = (for {
        activeContact <- s.active
      } yield p.messages.getOrElse(activeContact, Nil))
        .getOrElse(Nil)

      def messageSendingComponentSend(m: String) =
        (for {
          contact <- s.active
        } yield p.send(contact, m)).getOrElse(Callback.empty)

      <.div(
        ^.className := "container",
        <.div(
          ^.className := "row",
          <.div(
            ^.className := "col-3",
            UsersComponent.Component(
              UsersComponent
                .Props(p.user,
                       p.messages.keys.toSet,
                       u => $.modState(_.copy(active = Some(u))),
                       p.addUser,
                       s.active))
          ),
          <.div(^.className := "col-6",
                MessagesComponent.Component(
                  MessagesComponent.Props(p.user, discussionMessages))),
          <.div(^.className := "col-3",
                MessageSendingComponent.Component(
                  MessageSendingComponent.Props(
                    s.active.nonEmpty && p.user.nonEmpty,
                    messageSendingComponentSend)))
        )
      )
    }
  }

  val Component = ScalaComponent
    .builder[Props]("ChatComponent")
    .initialState(State.init)
    .renderBackend[Backend]
    .build
}
