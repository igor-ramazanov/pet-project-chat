package com.github.igorramazanov.chat.json
import cats.syntax.either._
import com.github.igorramazanov.chat.domain.ChatMessage.{
  GeneralChatMessage,
  IncomingChatMessage
}
import com.github.igorramazanov.chat.domain.User
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.util.Try

object DomainEntitiesSprayJsonSupport extends DomainEntitiesJsonSupport {
  override implicit def userJsonApi: JsonApi[User] = new JsonApi[User] {
    private implicit val jsonFormat: RootJsonFormat[User] = jsonFormat2(User)

    override def write(entity: User): String = entity.toJson.compactPrint

    override def read(jsonString: String): Either[String, User] =
      Try(jsonString.parseJson.convertTo[User]).toEither.leftMap(_.getMessage)
  }
  override implicit def incomingChatMessageJsonApi
    : JsonApi[IncomingChatMessage] =
    new JsonApi[IncomingChatMessage] {
      private implicit val jsonFormat: RootJsonFormat[IncomingChatMessage] =
        jsonFormat2(IncomingChatMessage)

      override def write(entity: IncomingChatMessage): String =
        entity.toJson.compactPrint
      override def read(
          jsonString: String): Either[String, IncomingChatMessage] =
        Try(jsonString.parseJson.convertTo[IncomingChatMessage]).toEither
          .leftMap(_.getMessage)
    }

  override implicit def generalChatMessageJsonApi: JsonApi[GeneralChatMessage] =
    new JsonApi[GeneralChatMessage] {
      private implicit val jsonFormat: RootJsonFormat[GeneralChatMessage] =
        jsonFormat4(GeneralChatMessage)

      override def write(entity: GeneralChatMessage): String =
        entity.toJson.compactPrint
      override def read(
          jsonString: String): Either[String, GeneralChatMessage] =
        Try(jsonString.parseJson.convertTo[GeneralChatMessage]).toEither
          .leftMap(_.getMessage)
    }
}
