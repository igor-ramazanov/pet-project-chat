package com.github.igorramazanov.chat.components

import com.github.igorramazanov.chat.domain.ChatMessage.GeneralChatMessage
import com.github.igorramazanov.chat.domain.User
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

@SuppressWarnings(Array("org.wartremover.warts.Any"))
object ChatComponent {

  final case class Props(
      user: Option[User],
      messages: Map[User.Id, List[GeneralChatMessage]],
      addUser: User.Id => Callback,
      send: (User.Id, String) => Callback
  )

  final case class State(activeContact: Option[User.Id])

  object State {
    def init: State = State(None)
  }

  final class Backend($ : BackendScope[Props, State]) {

    def render(p: Props, s: State): VdomElement = {
      val discussionMessages = (for {
        activeContact <- s.activeContact
      } yield p.messages.getOrElse(activeContact, Nil))
        .getOrElse(Nil)

      def messageSendingComponentSend(m: String) =
        (for {
          contact <- s.activeContact
        } yield p.send(contact, m)).getOrElse(Callback.empty)

      <.div(
        ^.className := "container",
        <.div(
          ^.className := "row",
          <.div(
            ^.className := "col-md-3",
            ContactsComponent.Component(
              ContactsComponent
                .Props(
                  p.user,
                  p.messages.keys.toSet,
                  u => $.modState(_.copy(activeContact = Some(u))),
                  p.addUser,
                  s.activeContact
                )
            )
          ),
          <.div(
            ^.className := "col-md-9",
            MessagesComponent.Component(MessagesComponent.Props(p.user, discussionMessages)),
            MessageSendingComponent.Component(
              MessageSendingComponent
                .Props(s.activeContact.nonEmpty && p.user.nonEmpty, messageSendingComponentSend)
            )
          )
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
