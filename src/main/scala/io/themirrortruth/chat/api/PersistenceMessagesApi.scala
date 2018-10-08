package io.themirrortruth.chat.api
import io.themirrortruth.chat.domain.ChatMessage.GeneralChatMessage
import simulacrum.typeclass

@typeclass trait PersistenceMessagesApi[F[_]] {
  def ofUserOrdered(id: String): F[List[GeneralChatMessage]]

  def save(id: String, message: GeneralChatMessage): F[Unit]
}
