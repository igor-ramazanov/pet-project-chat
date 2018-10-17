package io.github.igorramazanov.chat.json
import io.github.igorramazanov.chat.domain.{ChatMessage, User}
import spray.json._
import DefaultJsonProtocol._

import scala.util.Try

object DomainEntitiesSprayJsonSupport extends DomainEntitiesJsonSupport {
  override implicit def userJsonApi: JsonApi[User] = new JsonApi[User] {
    private val userFormat = jsonFormat2(User.apply)

    override def write(entity: User): String =
      userFormat.write(entity).compactPrint
    override def read(jsonString: String): Either[String, User] =
      Try(userFormat.read(jsonString.parseJson)).toEither.left.map(_.getMessage)
  }
  override implicit def incomingChatMessageJsonApi
    : JsonApi[ChatMessage.IncomingChatMessage] =
    new JsonApi[ChatMessage.IncomingChatMessage] {
      private val incomingChatMessageFormat = jsonFormat2(
        ChatMessage.IncomingChatMessage.apply)

      override def write(entity: ChatMessage.IncomingChatMessage): String =
        incomingChatMessageFormat.write(entity).compactPrint

      override def read(
          jsonString: String): Either[String, ChatMessage.IncomingChatMessage] =
        Try(incomingChatMessageFormat.read(jsonString.parseJson)).toEither.left
          .map(_.getMessage)
    }
  override implicit def generalChatMessageJsonApi
    : JsonApi[ChatMessage.GeneralChatMessage] =
    new JsonApi[ChatMessage.GeneralChatMessage] {
      private val generalChatMessageFormat = jsonFormat4(
        ChatMessage.GeneralChatMessage.apply)

      override def write(entity: ChatMessage.GeneralChatMessage): String =
        generalChatMessageFormat.write(entity).compactPrint
      override def read(
          jsonString: String): Either[String, ChatMessage.GeneralChatMessage] =
        Try(generalChatMessageFormat.read(jsonString.parseJson)).toEither.left
          .map(_.getMessage)
    }
}
