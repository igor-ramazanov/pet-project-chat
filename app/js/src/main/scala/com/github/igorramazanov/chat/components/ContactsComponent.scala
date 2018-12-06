package com.github.igorramazanov.chat.components

import cats.data.Validated
import com.github.igorramazanov.chat.domain.User
import com.github.igorramazanov.chat.domain.User.Implicits._
import cats.syntax.eq._
import com.github.igorramazanov.chat.validation.IdValidationError
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom.ext.KeyCode
import scalacss.ProdDefaults._
import scalacss.ScalaCssReact._
@SuppressWarnings(Array("org.wartremover.warts.Any"))
object ContactsComponent {
  object Styles extends StyleSheet.Inline {
    import dsl._
    val scroll = style(
      height(100.vh),
      maxHeight(100.vh),
      overflowX.hidden,
      overflowY.scroll
    )

    val contact = style(
      lineHeight(1),
      minHeight(40.px)
    )
  }

  final case class Props(user: Option[User],
                         contacts: Set[User.Id],
                         focus: User.Id => Callback,
                         addContact: User.Id => Callback,
                         active: Option[User.Id])

  final case class State(input: String,
                         isFirstTime: Boolean,
                         inputValidationErrors: List[String])

  object State {
    def init: State =
      State("",
            isFirstTime = true,
            List(IdValidationError.IsEmpty.errorMessage))
  }

  final class Backend($ : BackendScope[Props, State]) {
    private def validateNewContact: CallbackTo[Unit] =
      $.modState { (s, p) =>
        User.Id.validate(s.input) match {
          case Validated.Valid(id) =>
            if (p.user.exists(_.id === id)) {
              s.copy(inputValidationErrors = List("You can not add yourself"))
            } else {
              s.copy(inputValidationErrors = Nil)
            }
          case Validated.Invalid(errors) =>
            s.copy(
              inputValidationErrors =
                errors.toNonEmptyList.toList.map(_.errorMessage))
        }
      }

    private def clearAddNewContactInput =
      $.setState(State("", true, List(IdValidationError.IsEmpty.errorMessage)))

    private def setAddNewContactInput(text: String) =
      $.modState(_.copy(input = text, isFirstTime = false))

    private def isInvalid(user: Option[User],
                          input: String,
                          inputValidationErrors: List[String]) =
      user.exists(_.id.value == input) || inputValidationErrors.nonEmpty

    private def addNewContact(s: State)(
        e: ReactKeyboardEventFromInput): Callback = {
      val newContact = e.target.value

      $.props >>= { p: Props =>
        if (e.keyCode == KeyCode.Enter) {
          if (!isInvalid(p.user, newContact, s.inputValidationErrors)) {
            p.addContact(User.Id.unsafeCreate(newContact)) >> clearAddNewContactInput
          } else {
            Callback.empty
          }
        } else {
          Callback.empty
        }
      }
    }

    def render(p: Props, s: State): VdomElement = {
      def contact(u: User.Id) = {
        val isActive = p.active.contains(u)
        val className =
          s"list-group-item list-group-item-action${if (isActive) " active"
          else ""}"
        <.button(
          ^.`type` := "button",
          Styles.contact,
          ^.className := className,
          ^.onClick ==> { e: ReactEventFromHtml =>
            e.persist()
            e.preventDefault()
            p.focus(User.Id.unsafeCreate(e.target.textContent))
          },
          u.value
        )
      }

      val contacts = p.contacts.toList.sortBy(_.value).map(contact)

      val addNewContactInput = {
        val validationErrorsTagMods = if (s.inputValidationErrors.nonEmpty) {
          (^.className := "invalid-feedback") :: s.inputValidationErrors
            .map(vdomNodeFromString)
            .mkTagMod(<.br) :: Nil
        } else {
          (^.className := "invalid-feedback") :: Nil
        }

        <.div(
          <.input(
            ^.`type` := "text",
            ^.className := "w-100 form-control" + (if (!s.isFirstTime && isInvalid(
                                                         p.user,
                                                         s.input,
                                                         s.inputValidationErrors))
                                                     " is-invalid"
                                                   else ""),
            ^.placeholder := "Add new contact",
            ^.onKeyDown ==> addNewContact(s),
            ^.onChange ==> { e: ReactEventFromInput =>
              e.persist()
              setAddNewContactInput(e.target.value) >> validateNewContact
            },
            ^.value := s.input
          ),
          <.div(validationErrorsTagMods: _*)
        )
      }

      val header =
        <.span(^.className := "list-group-item text-center font-weight-bold",
               "Contacts")

      val tagMods = (^.className := "list-group") :: (Styles.scroll: TagMod) :: header :: addNewContactInput :: contacts

      <.div(tagMods: _*)
    }
  }

  val Component = ScalaComponent
    .builder[Props]("ContactsComponent")
    .initialState(State.init)
    .renderBackend[Backend]
    .build
}
