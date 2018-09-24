package io.themirrortruth.chat.api
import simulacrum.typeclass
import io.themirrortruth.chat.entity.ChatMessage.GeneralChatMessage

@typeclass trait OutgoingMesssagesApi[F[_]] {
  def send(generalChatMessage: GeneralChatMessage): F[Unit]
}
