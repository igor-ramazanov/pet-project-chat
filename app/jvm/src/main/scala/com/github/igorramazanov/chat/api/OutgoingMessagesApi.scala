package com.github.igorramazanov.chat.api
import com.github.igorramazanov.chat.domain.ChatMessage
import simulacrum.typeclass

@typeclass trait OutgoingMessagesApi[F[_]] {
  def send(generalChatMessage: ChatMessage.GeneralChatMessage): F[Unit]
}
