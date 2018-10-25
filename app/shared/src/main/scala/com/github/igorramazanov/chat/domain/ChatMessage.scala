package com.github.igorramazanov.chat.domain

sealed trait ChatMessage extends Product with Serializable

object ChatMessage {
  final case class IncomingChatMessage(to: String, payload: String)
      extends ChatMessage {
    def asGeneral(from: User, dateTimeEpochSeconds: Long) =
      GeneralChatMessage(from = from.id,
                         to = to,
                         payload = payload,
                         dateTimeUtcEpochSeconds = dateTimeEpochSeconds)
  }
  final case class GeneralChatMessage(from: String,
                                      to: String,
                                      payload: String,
                                      dateTimeUtcEpochSeconds: Long)
}
