package com.github.igorramazanov.chat.components

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom.ext.KeyCode
import scalacss.ProdDefaults._
import scalacss.ScalaCssReact._
@SuppressWarnings(Array("org.wartremover.warts.Any"))
object MessageSendingComponent {
  object Styles extends StyleSheet.Inline {
    import dsl._
    val input = style(
      position.sticky,
      bottom(10.px),
      zIndex(10),
      width :=! "calc(100% - 20px)",
      height(40.px)
    )
  }
  final case class Props(isActive: Boolean, send: String => Callback)

  final case class State(input: String, isFirstTime: Boolean) {
    def isInvalid: Boolean = !isFirstTime && input.isEmpty
  }

  object State {
    def init: State = State("", isFirstTime = true)
  }

  final class Backend($ : BackendScope[Props, State]) {
    private def onChange(e: ReactEventFromInput): Callback = {
      e.persist()
      $.modState(_.copy(input = e.target.value))
        .asEventDefault(e)
        .void
    }

    private def onSend(e: ReactKeyboardEventFromInput): Callback = {
      $.props >>= { p: Props =>
        val message = e.target.value
        if (e.keyCode == KeyCode.Enter) {
          if (message.nonEmpty) {
            p.send(message) >> $.modState(_.copy(input = ""))
          } else {
            $.modState(_.copy(isFirstTime = false))
          }
        } else {
          Callback.empty
        }
      }
    }

    def render(p: Props, s: State): VdomElement = {
      val invalidClass = if (s.isInvalid) " is-invalid" else ""
      val input =
        if (p.isActive)
          <.input(
            ^.`type` := "text",
            ^.className := s"form-control$invalidClass",
            Styles.input,
            ^.value := s.input,
            ^.onChange ==> onChange,
            ^.onKeyDown ==> onSend,
            ^.placeholder := "Type your message here"
          )
        else
          <.input(
            ^.`type` := "text",
            ^.className := s"form-control",
            Styles.input,
            ^.value := s.input,
            ^.onChange ==> onChange,
            ^.onKeyDown ==> onSend,
            ^.disabled := true
          )
      input
    }
  }

  val Component = ScalaComponent
    .builder[Props]("MessageSendComponent")
    .initialState(State.init)
    .renderBackend[Backend]
    .build
}
