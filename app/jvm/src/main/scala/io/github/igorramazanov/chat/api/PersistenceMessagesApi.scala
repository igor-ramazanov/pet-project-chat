package io.github.igorramazanov.chat.api
import io.github.igorramazanov.chat.domain.ChatMessage
import simulacrum.typeclass

@typeclass trait PersistenceMessagesApi[F[_]] {
  def ofUserOrdered(id: String): F[List[ChatMessage.GeneralChatMessage]]

  def save(id: String, message: ChatMessage.GeneralChatMessage): F[Unit]
}
