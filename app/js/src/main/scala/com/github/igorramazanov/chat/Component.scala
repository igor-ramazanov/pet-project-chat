package com.github.igorramazanov.chat

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

@SuppressWarnings(Array("org.wartremover.warts.Any"))
object Component {

  final case class Props() {
    @inline def render: VdomElement = Component(this)
  }

  //implicit val reusabilityProps: Reusability[Props] =
  //  Reusability.derive

  final class Backend($ : BackendScope[Props, Unit]) {
    def render(p: Props): VdomElement =
      <.div(<.input(^.`type` := "text", ^.value := "some text"))
  }

  val Component = ScalaComponent
    .builder[Props]("Component")
    .renderBackend[Backend]
    //.configure(Reusability.shouldComponentUpdate)
    .build
}
