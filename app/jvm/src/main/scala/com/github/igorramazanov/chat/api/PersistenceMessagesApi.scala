package com.github.igorramazanov.chat.api
import com.github.igorramazanov.chat.domain.{ChatMessage, User}
import org.reactivestreams.{Publisher, Subscriber}
import simulacrum.typeclass

@typeclass trait PersistenceMessagesApi[F[_]] {
  def ofUserOrdered(id: User.Id): F[Publisher[ChatMessage.GeneralChatMessage]]

  def save(): F[Subscriber[ChatMessage.GeneralChatMessage]]
}
