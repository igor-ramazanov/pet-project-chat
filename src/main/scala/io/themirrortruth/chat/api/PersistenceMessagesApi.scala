package io.themirrortruth.chat.api
import io.themirrortruth.chat.domain.User
import simulacrum.typeclass
import io.themirrortruth.chat.domain.ChatMessage.GeneralChatMessage

@typeclass trait PersistenceMessagesApi[F[_]] {
  def ofUserOrdered(user: User): F[List[GeneralChatMessage]]

  def save(user: User, message: GeneralChatMessage): F[Unit]
}
