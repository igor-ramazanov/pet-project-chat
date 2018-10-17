package io.github.igorramazanov.chat.api
import io.github.igorramazanov.chat.domain.ChatMessage
import simulacrum.typeclass

@typeclass trait OutgoingMessagesApi[F[_]] {
  def send(generalChatMessage: ChatMessage.GeneralChatMessage): F[Unit]
}
