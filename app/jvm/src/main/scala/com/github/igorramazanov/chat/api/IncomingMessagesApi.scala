package com.github.igorramazanov.chat.api
import com.github.igorramazanov.chat.domain.ChatMessage
import org.reactivestreams.Publisher

trait IncomingMessagesApi {
  def subscribe(id: String): Publisher[ChatMessage.GeneralChatMessage]
}
