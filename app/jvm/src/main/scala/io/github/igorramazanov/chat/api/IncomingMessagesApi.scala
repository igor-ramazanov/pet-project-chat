package io.github.igorramazanov.chat.api
import io.github.igorramazanov.chat.domain.ChatMessage
import org.reactivestreams.Publisher

trait IncomingMessagesApi {
  def subscribe(id: String): Publisher[ChatMessage.GeneralChatMessage]
}
