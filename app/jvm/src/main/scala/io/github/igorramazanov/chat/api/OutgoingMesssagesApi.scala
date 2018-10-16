package io.github.igorramazanov.chat.api
import simulacrum.typeclass
import io.github.igorramazanov.chat.domain.ChatMessage.GeneralChatMessage

@typeclass trait OutgoingMesssagesApi[F[_]] {
  def send(generalChatMessage: GeneralChatMessage): F[Unit]
}
