package io.themirrortruth.chat.api
import io.themirrortruth.chat.entity.User
import simulacrum.typeclass
import io.themirrortruth.chat.entity.ChatMessage.GeneralChatMessage

@typeclass trait PersistenceMessagesApi[F[_]] {
  def ofUserOrdered(user: User): F[List[GeneralChatMessage]]

  def save(user: User, message: GeneralChatMessage): F[Unit]
}
