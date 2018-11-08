package com.github.igorramazanov.chat.api
import com.github.igorramazanov.chat.domain.{ChatMessage, User}
import simulacrum.typeclass

@typeclass trait PersistenceMessagesApi[F[_]] {
  def ofUserOrdered(id: User.Id): F[List[ChatMessage.GeneralChatMessage]]

  def save(id: User.Id, message: ChatMessage.GeneralChatMessage): F[Unit]
}
