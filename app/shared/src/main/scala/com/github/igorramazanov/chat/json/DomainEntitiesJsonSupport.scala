package com.github.igorramazanov.chat.json

import cats.data.NonEmptyChain
import com.github.igorramazanov.chat.domain.ChatMessage.{
  GeneralChatMessage,
  IncomingChatMessage
}
import com.github.igorramazanov.chat.domain._

trait DomainEntitiesJsonSupport {
  implicit def invalidSignUpRequestJsonApi: JsonApi[InvalidRequest]
  implicit def signUpRequestJsonApi: JsonApi[SignUpOrInRequest]
  implicit def validSignUpRequestJsonApi: JsonApi[ValidSignUpOrInRequest]
  implicit def userJsonApi: JsonApi[User]
  implicit def incomingChatMessageJsonApi
      : JsonApi[ChatMessage.IncomingChatMessage]
  implicit def generalChatMessageJsonApi
      : JsonApi[ChatMessage.GeneralChatMessage]
}

object DomainEntitiesJsonSupport {
  implicit class StringOps(val s: String) extends AnyVal {
    def toInvalidSignUpRequest(implicit
        jsonApi: JsonApi[InvalidRequest]
    ): Either[NonEmptyChain[String], InvalidRequest] =
      jsonApi.read(s)

    def toValidSignUpRequest(implicit
        jsonApi: JsonApi[ValidSignUpOrInRequest]
    ): Either[NonEmptyChain[String], ValidSignUpOrInRequest] =
      jsonApi.read(s)

    def toSignUpRequest(implicit
        jsonApi: JsonApi[SignUpOrInRequest]
    ): Either[NonEmptyChain[String], SignUpOrInRequest] =
      jsonApi.read(s)

    def toGeneralMessage(implicit
        jsonApi: JsonApi[GeneralChatMessage]
    ): Either[NonEmptyChain[String], GeneralChatMessage] = jsonApi.read(s)

    def toIncomingMessage(implicit
        jsonApi: JsonApi[IncomingChatMessage]
    ): Either[NonEmptyChain[String], IncomingChatMessage] = jsonApi.read(s)

    def toUser(implicit
        jsonApi: JsonApi[User]
    ): Either[NonEmptyChain[String], User] =
      jsonApi.read(s)
  }

  implicit class InvalidSignUpRequestOps(val r: InvalidRequest) extends AnyVal {
    def toJson(implicit jsonApi: JsonApi[InvalidRequest]): String =
      jsonApi.write(r)
  }

  implicit class ValidSignUpRequestOps(val r: ValidSignUpOrInRequest)
      extends AnyVal {
    def toJson(implicit jsonApi: JsonApi[ValidSignUpOrInRequest]): String =
      jsonApi.write(r)
  }

  implicit class SignUpRequestOps(val r: SignUpOrInRequest) extends AnyVal {
    def toJson(implicit jsonApi: JsonApi[SignUpOrInRequest]): String =
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
