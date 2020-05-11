package com.github.igorramazanov.chat.components

import com.github.igorramazanov.chat.components.MainComponent.Alert
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import scalacss.ProdDefaults._
import scalacss.ScalaCssReact._

@SuppressWarnings(Array("org.wartremover.warts.Any"))
object AlertsComponent {

  object Styles extends StyleSheet.Inline {
    import dsl._
    val alertsRightSidebar = style(
      height(100.vh),
      position.fixed,
      width(400.px),
      top(25.px),
      right(50.px)
    )
  }

  final case class Props(alerts: List[Alert], removeAlert: String => Callback)

  final class Backend($ : BackendScope[Props, Unit]) {
    private val multiplicationSymbol = 10005.toChar.toString

    def render(p: Props): VdomElement = {
      def alert(alert: Alert) =
        <.div(
          ^.className := s"alert alert-dismissible ${alert.`type`.className}",
          alert.content,
          <.button(
            ^.`type` := "button",
            ^.className := "close",
            ^.onClick --> p.removeAlert(alert.id),
            <.span(multiplicationSymbol)
          )
        )

      val alerts  = p.alerts.map(alert)
      val tagMods =
        (^.className := "list-group") :: (Styles.alertsRightSidebar: TagMod) :: alerts

      <.div(tagMods: _*)
    }
  }

  val Component = ScalaComponent
    .builder[Props]("AlertsComponent")
    .renderBackend[Backend]
    .build
}
