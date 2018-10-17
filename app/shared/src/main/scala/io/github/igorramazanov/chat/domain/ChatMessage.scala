package io.github.igorramazanov.chat.domain
import java.time.{LocalDateTime, ZoneId, ZoneOffset}

sealed trait ChatMessage extends Product with Serializable

object ChatMessage {
  final case class IncomingChatMessage(to: String, payload: String)
      extends ChatMessage {
    def asGeneral(from: User) =
      GeneralChatMessage(
        from = from.id,
        to = to,
        payload = payload,
        dateTimeUtcEpochSeconds =
          LocalDateTime.now(ZoneId.of("Z")).toEpochSecond(ZoneOffset.UTC))
  }
  final case class GeneralChatMessage(from: String,
                                      to: String,
                                      payload: String,
                                      dateTimeUtcEpochSeconds: Long)
}
