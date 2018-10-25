package com.github.igorramazanov.chat.json

import com.github.igorramazanov.chat.domain.ChatMessage.{
  GeneralChatMessage,
  IncomingChatMessage
}
import com.github.igorramazanov.chat.domain.{ChatMessage, User}

trait DomainEntitiesJsonSupport {
  implicit def userJsonApi: JsonApi[User]
  implicit def incomingChatMessageJsonApi
    : JsonApi[ChatMessage.IncomingChatMessage]
  implicit def generalChatMessageJsonApi
    : JsonApi[ChatMessage.GeneralChatMessage]
}

object DomainEntitiesJsonSupport {
  implicit class StringOps(val s: String) extends AnyVal {
    def toGeneralMessage(implicit jsonApi: JsonApi[GeneralChatMessage])
      : Either[String, GeneralChatMessage] = jsonApi.read(s)

    def toIncomingMessage(implicit jsonApi: JsonApi[IncomingChatMessage])
      : Either[String, IncomingChatMessage] = jsonApi.read(s)

    def toUser(implicit jsonApi: JsonApi[User]): Either[String, User] =
      jsonApi.read(s)
  }

  implicit class GeneralChatMessageOps(val m: GeneralChatMessage)
      extends AnyVal {
    def toJson(implicit jsonApi: JsonApi[GeneralChatMessage]): String =
      jsonApi.write(m)
  }
  implicit class IncomingChatMessageOps(val m: IncomingChatMessage)
      extends AnyVal {
    def toJson(implicit jsonApi: JsonApi[IncomingChatMessage]): String =
      jsonApi.write(m)
  }
  implicit class UserOps(val m: User) extends AnyVal {
    def toJson(implicit jsonApi: JsonApi[User]): String =
      jsonApi.write(m)
  }
}
