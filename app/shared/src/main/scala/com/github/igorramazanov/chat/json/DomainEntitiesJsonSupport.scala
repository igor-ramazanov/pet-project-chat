package com.github.igorramazanov.chat.json

import cats.data.NonEmptyChain
import com.github.igorramazanov.chat.domain.ChatMessage.{
  GeneralChatMessage,
  IncomingChatMessage
}
import com.github.igorramazanov.chat.domain.{
  ChatMessage,
  InvalidSignUpRequest,
  SignUpRequest,
  User
}

trait DomainEntitiesJsonSupport {
  implicit def invalidSignUpRequestJsonApi: JsonApi[InvalidSignUpRequest]
  implicit def signUpRequestJsonApi: JsonApi[SignUpRequest]
  implicit def userJsonApi: JsonApi[User]
  implicit def incomingChatMessageJsonApi
    : JsonApi[ChatMessage.IncomingChatMessage]
  implicit def generalChatMessageJsonApi
    : JsonApi[ChatMessage.GeneralChatMessage]
}

object DomainEntitiesJsonSupport {
  implicit class StringOps(val s: String) extends AnyVal {
    def toInvalidSignUpRequest(implicit jsonApi: JsonApi[InvalidSignUpRequest])
      : Either[NonEmptyChain[String], InvalidSignUpRequest] =
      jsonApi.read(s)

    def toSignUpRequest(implicit jsonApi: JsonApi[SignUpRequest])
      : Either[NonEmptyChain[String], SignUpRequest] =
      jsonApi.read(s)

    def toGeneralMessage(implicit jsonApi: JsonApi[GeneralChatMessage])
      : Either[NonEmptyChain[String], GeneralChatMessage] = jsonApi.read(s)

    def toIncomingMessage(implicit jsonApi: JsonApi[IncomingChatMessage])
      : Either[NonEmptyChain[String], IncomingChatMessage] = jsonApi.read(s)

    def toUser(
        implicit jsonApi: JsonApi[User]): Either[NonEmptyChain[String], User] =
      jsonApi.read(s)
  }

  implicit class InvalidSignUpRequestOps(val r: InvalidSignUpRequest)
      extends AnyVal {
    def toJson(implicit jsonApi: JsonApi[InvalidSignUpRequest]): String =
      jsonApi.write(r)
  }

  implicit class SignUpRequestOps(val r: SignUpRequest) extends AnyVal {
    def toJson(implicit jsonApi: JsonApi[SignUpRequest]): String =
      jsonApi.write(r)
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
  implicit class UserOps(val u: User) extends AnyVal {
    def toJson(implicit jsonApi: JsonApi[User]): String =
      jsonApi.write(u)
  }
}
