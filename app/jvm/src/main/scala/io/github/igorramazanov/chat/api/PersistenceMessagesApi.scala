package io.github.igorramazanov.chat.api
import io.github.igorramazanov.chat.domain.ChatMessage.GeneralChatMessage
import simulacrum.typeclass

@typeclass trait PersistenceMessagesApi[F[_]] {
  def ofUserOrdered(id: String): F[List[GeneralChatMessage]]

  def save(id: String, message: GeneralChatMessage): F[Unit]
}
