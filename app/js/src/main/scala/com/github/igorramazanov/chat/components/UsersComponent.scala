package com.github.igorramazanov.chat.components

import com.github.igorramazanov.chat.domain.User
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom.ext.KeyCode

@SuppressWarnings(Array("org.wartremover.warts.Any"))
object UsersComponent {

  final case class Props(user: Option[User],
                         contacts: Set[String],
                         focus: String => Callback,
                         addContact: String => Callback,
                         active: Option[String])

  final case class State(input: String, isFirstTime: Boolean)

  object State {
    def init: State = State("", isFirstTime = true)
  }

  final class Backend($ : BackendScope[Props, State]) {

    private def clearAddNewContactInput = $.modState(_.copy(input = ""))

    private def setAddNewContactInput(text: String) =
      $.modState(_.copy(input = text))

    private def isInvalid(user: Option[User], input: String) =
      input.isEmpty || user.exists(_.id == input)

    private def addNewContact(e: ReactKeyboardEventFromInput): Callback = {
      val newContact = e.target.value

      $.props >>= { p: Props =>
        if (e.keyCode == KeyCode.Enter) {
          if (!isInvalid(p.user, newContact)) {
            p.addContact(newContact) >> clearAddNewContactInput
          } else {
            $.modState(_.copy(isFirstTime = false))
          }
        } else {
          Callback.empty
        }
      }
    }

    def render(p: Props, s: State): VdomElement = {
      def contact(u: String) = {
        val isActive = p.active.contains(u)
        val className =
          s"list-group-item list-group-item-action${if (isActive) " active"
          else ""}"
        <.button(
          ^.`type` := "button",
          ^.className := className,
          ^.onClick ==> { e: ReactEventFromHtml =>
            e.persist()
            e.preventDefault()
            p.focus(e.target.textContent)
          },
          u
        )
      }

      val contacts = p.contacts.toList.sorted.map(contact)

      val addNewContactInput = <.input(
        ^.`type` := "text",
        ^.className := "w-100 form-control" + (if (!s.isFirstTime && isInvalid(
                                                     p.user,
                                                     s.input))
                                                 " is-invalid"
                                               else ""),
        ^.placeholder := "Add new contact",
        ^.onKeyDown ==> addNewContact,
        ^.onChange ==> { e: ReactEventFromInput =>
          e.persist()
          setAddNewContactInput(e.target.value)
        },
        ^.value := s.input
      )

      val header = <.span(^.className := "list-group-item", "Contacts")

      val tagMods = (^.className := "list-group") :: header :: addNewContactInput :: contacts

      <.div(tagMods: _*)
    }
  }

  val Component = ScalaComponent
    .builder[Props]("UsersComponent")
    .initialState(State.init)
    .renderBackend[Backend]
    .build
}
