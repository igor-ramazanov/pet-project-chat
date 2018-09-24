package io.themirrortruth.chat.api
import io.themirrortruth.chat.entity.User
import io.themirrortruth.chat.entity.ChatMessage.GeneralChatMessage
import org.reactivestreams.Publisher

trait IncomingMessagesApi {
  def subscribe(user: User): Publisher[GeneralChatMessage]
}
