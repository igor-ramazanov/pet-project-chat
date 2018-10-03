package io.themirrortruth.chat.api
import io.themirrortruth.chat.domain.User
import io.themirrortruth.chat.domain.ChatMessage.GeneralChatMessage
import org.reactivestreams.Publisher

trait IncomingMessagesApi {
  def subscribe(user: User): Publisher[GeneralChatMessage]
}
