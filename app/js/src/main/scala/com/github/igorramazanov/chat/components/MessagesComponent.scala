package com.github.igorramazanov.chat.components

import com.github.igorramazanov.chat.domain.ChatMessage.GeneralChatMessage
import com.github.igorramazanov.chat.domain.User
import com.github.igorramazanov.chat.domain.User.Implicits._
import cats.syntax.eq._
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import scala.concurrent.duration._
import scala.scalajs.js.Date
import scalacss.ProdDefaults._
import scalacss.ScalaCssReact._
@SuppressWarnings(Array("org.wartremover.warts.Any"))
object MessagesComponent {
  object Styles extends StyleSheet.Inline {
    import dsl._
    val messageMe = style(
      alignSelf.flexEnd
    )
    val messageFriend = style(
      alignSelf.flexStart
    )
    val message = style(
      marginBottom(5.px),
      borderRadius(10.px).important,
      width(60.%%)
    )

    val messagesScroll = style(
      height :=! "calc(100vh - 65px)",
      maxHeight(100.vh),
      overflowX.hidden,
      overflowY.scroll,
      borderRadius(0.px)
    )

    val hideScrollbar = style(
      display.none
    )

    val time = style(
      fontSize(14.px)
    )
  }
  final case class Props(user: Option[User], messages: List[GeneralChatMessage])

  final class Backend($ : BackendScope[Props, Unit]) {
    private val autoScrollToBottomRef = Ref[org.scalajs.dom.Element]

    def scroll: Callback =
      autoScrollToBottomRef.foreach(_.scrollIntoView())

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
        <.div(
          ^.className := s"list-group-item",
          Styles.message,
          if (user.id === m.from) Styles.messageMe
          else Styles.messageFriend,
          <.p(p, ^.className := "mb-2"),
          <.div(^.className := "d-flex justify-content-end",
                <.i(Styles.time, time))
        )
      }

      val tagMods = (^.className := "list-group scroll-messages py-4") :: (Styles.messagesScroll: TagMod) :: p.user.toList
        .flatMap(u => p.messages.map(message(u))) ::: <.div()
        .withRef(autoScrollToBottomRef) :: Nil
      <.div(tagMods: _*)
    }
  }

  val Component = ScalaComponent
    .builder[Props]("MessagesComponent")
    .renderBackend[Backend]
    .componentDidUpdate(_.backend.scroll)
    .build
}
