package com.github.igorramazanov.chat.components

import com.github.igorramazanov.chat.domain.ChatMessage.GeneralChatMessage
import com.github.igorramazanov.chat.domain.User
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import scala.concurrent.duration._
import scala.scalajs.js.Date

@SuppressWarnings(Array("org.wartremover.warts.Any"))
object MessagesComponent {

  final case class Props(user: Option[User], messages: List[GeneralChatMessage])

  final class Backend($ : BackendScope[Props, Unit]) {
    def render(p: Props): VdomElement = {
      def message(user: User)(m: GeneralChatMessage) = {
        val p = m.payload
        val t = (m.dateTimeUtcEpochSeconds * 1000L).toDouble
        val date = new Date(t)

        def padd(n: Int): String = ("0" + n.toString).takeRight(2)

        val timeString = padd(date.getHours()) + ":" +
          padd(date.getMinutes()) + ":" +
          padd(date.getSeconds())
        val dateString = padd(date.getDate()) + "/" +
          padd(date.getMonth() + 1) + "/" +
          date.getFullYear().toString

        def isOlderThan1Day(utcEpochSeconds: Long): Boolean = {
          val curr = Date.now()
          val dayAgo = (curr - 1.day.toMillis) / 1000
          utcEpochSeconds < dayAgo
        }

        val time = timeString + (if (isOlderThan1Day(m.dateTimeUtcEpochSeconds))
                                   " " + dateString
                                 else "")
        val from = if (user.id == m.from) "-me" else "-friend"
        <.div(
          ^.className := s"list-group-item message message$from",
//          <.b(m.from + ": "),
          <.p(p, ^.className := "mb-2"),
          <.div(^.className := "d-flex justify-content-end", <.i(time))
        )
      }

      val tagMods = (^.className := "list-group scroll-messages py-4") :: p.user.toList
        .flatMap(u => p.messages.map(message(u)))
      <.div(tagMods: _*)
    }
  }

  val Component = ScalaComponent
    .builder[Props]("MessagesComponent")
    .renderBackend[Backend]
    .build
}
