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
import scalacss.DevDefaults._
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
  final case class Props(signIn: (String, String) => Callback,
                         signUp: (String, String) => Callback,
                         isInFlight: Boolean)

  final case class State(username: String,
                         usernameValidationErrors: List[String],
                         password: String,
                         passwordValidationErrors: List[String],
                         isFirstTime: Boolean) {
    def isValid: Boolean =
      usernameValidationErrors.isEmpty && passwordValidationErrors.isEmpty
  }

  object State {
    def init: State = State("", Nil, "", Nil, isFirstTime = true)
  }

  final class Backend($ : BackendScope[Props, State]) {
    private def validateUsername: CallbackTo[Unit] =
      $.modState { s =>
        User.Id.validate(s.username) match {
          case Validated.Valid(_) => s.copy(usernameValidationErrors = Nil)
          case Validated.Invalid(errors) =>
            s.copy(
              usernameValidationErrors =
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

    private def signIn: CallbackTo[Unit] =
      for {
        p <- $.props
        s <- $.state
        _ <- if (s.isValid) p.signIn(s.username, s.password)
        else Callback.empty
      } yield ()

    private def signUp: CallbackTo[Unit] =
      for {
        p <- $.props
        s <- $.state
        _ <- if (s.isValid) p.signUp(s.username, s.password)
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

      val usernameValidationErrors =
        <.div(validationErrorsTagMods(s.usernameValidationErrors): _*)
      val passwordValidationErrors =
        <.div(validationErrorsTagMods(s.passwordValidationErrors): _*)

      <.div(
        ^.className := "container",
        <.div(
          ^.className := "row align-items-center",
          <.div(
            ^.className := "col-10 col-md-6 offset-md-3 d-flex justify-content-center my-2",
            Styles.welcome,
            <.div(
              <.input(
                ^.`type` := "text",
                ^.className := inputClass(s.isFirstTime,
                                          s.usernameValidationErrors.isEmpty),
                ^.placeholder := "Username",
                ^.value := s.username,
                ^.onChange ==> { e: ReactEventFromInput =>
                  e.persist()
                  $.modState(_.copy(username = e.target.value,
                                    isFirstTime = false)) >> validateUsername
                }
              ),
              usernameValidationErrors
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
