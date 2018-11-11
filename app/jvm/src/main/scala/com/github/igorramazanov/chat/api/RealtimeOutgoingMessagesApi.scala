package com.github.igorramazanov.chat.api
import com.github.igorramazanov.chat.domain.ChatMessage
import org.reactivestreams.Subscriber
import simulacrum.typeclass

@typeclass trait RealtimeOutgoingMessagesApi[F[_]] {
  def send(): F[Subscriber[ChatMessage.GeneralChatMessage]]
}
