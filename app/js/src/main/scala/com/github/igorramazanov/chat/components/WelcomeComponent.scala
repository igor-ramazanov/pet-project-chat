package com.github.igorramazanov.chat.components
import cats.data.Validated
import com.github.igorramazanov.chat.domain.User
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{
  BackendScope,
  Callback,
  CallbackTo,
  ReactEventFromInput,
  ScalaComponent
}
import scalacss.ProdDefaults._
import scalacss.ScalaCssReact._

@SuppressWarnings(Array("org.wartremover.warts.Any"))
object WelcomeComponent {
  object Styles extends StyleSheet.Inline {
    import dsl._
    val welcome = style(
      height(100.vh),
      flexDirection.column
    )
  }
  final case class Props(
      signIn: (User.Id, User.Email, User.Password) => Callback,
      signUp: (User.Id, User.Email, User.Password) => Callback,
      isInFlight: Boolean)

  final case class State(id: String,
                         idValidationErrors: List[String],
                         password: String,
                         passwordValidationErrors: List[String],
                         email: String,
                         emailValidationErrors: List[String],
                         isFirstTime: Boolean) {
    def isValid: Boolean =
      idValidationErrors.isEmpty &&
        passwordValidationErrors.isEmpty &&
        emailValidationErrors.isEmpty
  }

  object State {
    def init: State = State("", Nil, "", Nil, "", Nil, isFirstTime = true)
  }

  final class Backend($ : BackendScope[Props, State]) {
    private def validateId: CallbackTo[Unit] =
      $.modState { s =>
        User.Id.validate(s.id) match {
          case Validated.Valid(_) => s.copy(idValidationErrors = Nil)
          case Validated.Invalid(errors) =>
            s.copy(
              idValidationErrors =
                errors.toNonEmptyList.toList.map(_.errorMessage)
            )
        }
      }

    private def validatePassword: CallbackTo[Unit] =
      $.modState { s =>
        User.Password.validate(s.password) match {
          case Validated.Valid(_) =>
            s.copy(passwordValidationErrors = Nil)
          case Validated.Invalid(errors) =>
            s.copy(
              passwordValidationErrors =
                errors.toNonEmptyList.toList.map(_.errorMessage)
            )
        }
      }

    private def validateEmail: CallbackTo[Unit] =
      $.modState { s =>
        User.Email.validate(s.email) match {
          case Validated.Valid(_) =>
            s.copy(emailValidationErrors = Nil)
          case Validated.Invalid(errors) =>
            s.copy(
              emailValidationErrors =
                errors.toNonEmptyList.toList.map(_.errorMessage)
            )
        }
      }

    private def signIn: CallbackTo[Unit] =
      for {
        p <- $.props
        s <- $.state
        _ <- if (s.isValid)
          p.signIn(User.Id.unsafeCreate(s.id),
                   User.Email.unsafeCreate(s.email),
                   User.Password.unsafeCreate(s.password))
        else Callback.empty
      } yield ()

    private def signUp: CallbackTo[Unit] =
      for {
        p <- $.props
        s <- $.state
        _ <- if (s.isValid)
          p.signUp(User.Id.unsafeCreate(s.id),
                   User.Email.unsafeCreate(s.email),
                   User.Password.unsafeCreate(s.password))
        else Callback.empty
      } yield ()

    private def inputClass(isFirstTime: Boolean, isValid: Boolean) =
      if (!isFirstTime && !isValid) "form-control is-invalid"
      else "form-control"

    private def button(isInFlight: Boolean, text: String, c: Callback) = {
      if (isInFlight) {
        <.button(^.disabled := true,
                 ^.className := "btn btn-primary",
                 ^.onClick --> c,
                 text)
      } else {
        <.button(^.className := "btn btn-primary", ^.onClick --> c, text)
      }
    }

    def render(p: Props, s: State): VdomElement = {
      def validationErrorsTagMods(validationErrors: List[String]) =
        if (validationErrors.nonEmpty)
          (^.className := "invalid-feedback") :: validationErrors
            .map(vdomNodeFromString)
            .mkTagMod(<.br) :: Nil
        else (^.className := "invalid-feedback") :: Nil

      val idValidationErrors =
        <.div(validationErrorsTagMods(s.idValidationErrors): _*)
      val passwordValidationErrors =
        <.div(validationErrorsTagMods(s.passwordValidationErrors): _*)
      val emailValidationErrors =
        <.div(validationErrorsTagMods(s.emailValidationErrors): _*)

      <.div(
        ^.className := "container",
        <.div(
          ^.className := "row align-items-center",
          <.div(
            ^.className := "col-10 col-md-6 offset-md-3 d-flex justify-content-center",
            Styles.welcome,
            <.div(
              ^.className := "my-2",
              <.input(
                ^.`type` := "text",
                ^.className := inputClass(s.isFirstTime,
                                          s.idValidationErrors.isEmpty),
                ^.placeholder := "Nickname",
                ^.value := s.id,
                ^.onChange ==> { e: ReactEventFromInput =>
                  e.persist()
                  $.modState(_.copy(id = e.target.value, isFirstTime = false)) >> validateId
                }
              ),
              idValidationErrors
            ),
            <.div(
              ^.className := "my-2",
              <.input(
                ^.`type` := "text",
                ^.className := inputClass(s.isFirstTime,
                                          s.emailValidationErrors.isEmpty),
                ^.placeholder := "Email",
                ^.value := s.email,
                ^.onChange ==> { e: ReactEventFromInput =>
                  e.persist()
                  $.modState(_.copy(email = e.target.value,
                                    isFirstTime = false)) >> validateEmail
                }
              ),
              emailValidationErrors
            ),
            <.div(
              ^.className := "my-2",
              <.input(
                ^.`type` := "password",
                ^.className := inputClass(s.isFirstTime,
                                          s.passwordValidationErrors.isEmpty),
                ^.placeholder := "Password",
                ^.value := s.password,
                ^.onChange ==> { e: ReactEventFromInput =>
                  e.persist()
                  $.modState(_.copy(password = e.target.value,
                                    isFirstTime = false)) >> validatePassword
                }
              ),
              passwordValidationErrors
            ),
            <.div(
              ^.className := "d-flex justify-content-between my-2",
              button(p.isInFlight, "Sign In", signIn),
              button(p.isInFlight, "Sign Up", signUp)
            )
          )
        )
      )
    }
  }

  val Component = ScalaComponent
    .builder[Props]("Welcome")
    .initialState(State.init)
    .renderBackend[Backend]
    //.configure(Reusability.shouldComponentUpdate)
    .build
}
