package com.github.igorramazanov.chat.components
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{
  BackendScope,
  Callback,
  ReactEventFromInput,
  ScalaComponent
}

@SuppressWarnings(Array("org.wartremover.warts.Any"))
object WelcomeComponent {

  final case class Props(signIn: (String, String) => Callback,
                         signUp: (String, String) => Callback)

  final case class State(username: String,
                         password: String,
                         isFirstTime: Boolean)

  object State {
    def init: State = State("", "", isFirstTime = true)
  }

  final class Backend($ : BackendScope[Props, State]) {
    private def validate(onSuccess: (String, String) => Callback): Callback =
      (for {
        s <- $.state
      } yield {
        if (s.username.isEmpty || s.password.isEmpty) {
          $.modState(_.copy(isFirstTime = false))
        } else {
          onSuccess(s.username, s.password)
        }
      }).flatten

    private def signIn: Callback = $.props.flatMap(p => validate(p.signIn))

    private def signUp: Callback = $.props.flatMap(p => validate(p.signUp))

    def inputClass(isFirstTime: Boolean, str: String): String =
      if (!isFirstTime && str.isEmpty) "form-control is-invalid"
      else "form-control"

    def render(s: State): VdomElement = {

      <.div(
        ^.className := "container",
        <.div(
          ^.className := "row align-items-center",
          <.div(
            ^.className := "col",
            <.div(
              ^.className := "form-group",
              <.input(
                ^.`type` := "text",
                ^.className := inputClass(s.isFirstTime, s.username),
                ^.placeholder := "Username",
                ^.value := s.username,
                ^.onChange ==> { e: ReactEventFromInput =>
                  e.persist()
                  $.modState(_.copy(username = e.target.value))
                }
              )
            ),
            <.div(
              ^.className := "form-group",
              <.input(
                ^.`type` := "password",
                ^.className := inputClass(s.isFirstTime, s.password),
                ^.placeholder := "Password",
                ^.value := s.password,
                ^.onChange ==> { e: ReactEventFromInput =>
                  e.persist()
                  $.modState(_.copy(password = e.target.value))
                }
              )
            ),
            <.div(
              ^.className := "container",
              <.div(
                ^.className := "row justify-content-center",
                <.button(^.className := "col-sm btn btn-primary",
                         ^.onClick --> signIn,
                         "Sign in"),
                <.div(^.className := "col-sm"),
                <.button(^.className := "col-sm btn btn-primary",
                         ^.onClick --> signUp,
                         "Sign up")
              )
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
