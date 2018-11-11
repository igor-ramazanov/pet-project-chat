package com.github.igorramazanov.chat.api
import com.github.igorramazanov.chat.domain.{ChatMessage, User}
import org.reactivestreams.Publisher
import simulacrum.typeclass

@typeclass trait RealtimeIncomingMessagesApi[F[_]] {
  def subscribe(id: User.Id): F[Publisher[ChatMessage.GeneralChatMessage]]
}
