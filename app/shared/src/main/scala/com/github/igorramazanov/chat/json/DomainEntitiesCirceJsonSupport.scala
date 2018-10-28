//package com.github.igorramazanov.chat.json
//import com.github.igorramazanov.chat.domain.{ChatMessage, User}
//import io.circe.generic.semiauto._
//import io.circe.parser.decode
//import io.circe.syntax._
//import io.circe.{Decoder, Encoder}
//
//object DomainEntitiesCirceJsonSupport extends DomainEntitiesJsonSupport {
//  override implicit def userJsonApi: JsonApi[User] = new JsonApi[User] {
//    implicit val decoder: Decoder[User] = deriveDecoder
//    implicit val encoder: Encoder[User] = deriveEncoder
//
//    override def write(entity: User): String = entity.asJson.noSpaces
//
//    override def read(jsonString: String): Either[String, User] =
//      decode[User](jsonString).left.map(_.getMessage)
//  }
//  override implicit def incomingChatMessageJsonApi
//    : JsonApi[ChatMessage.IncomingChatMessage] =
//    new JsonApi[ChatMessage.IncomingChatMessage] {
//      implicit val decoder: Decoder[ChatMessage.IncomingChatMessage] =
//        deriveDecoder
//      implicit val encoder: Encoder[ChatMessage.IncomingChatMessage] =
//        deriveEncoder
//
//      override def write(entity: ChatMessage.IncomingChatMessage): String =
//        entity.asJson.noSpaces
//      override def read(
//          jsonString: String): Either[String, ChatMessage.IncomingChatMessage] =
//        decode[ChatMessage.IncomingChatMessage](jsonString).left
//          .map(_.getMessage)
//    }
//  override implicit def generalChatMessageJsonApi
//    : JsonApi[ChatMessage.GeneralChatMessage] =
//    new JsonApi[ChatMessage.GeneralChatMessage] {
//      implicit val decoder: Decoder[ChatMessage.GeneralChatMessage] =
//        deriveDecoder
//      implicit val encoder: Encoder[ChatMessage.GeneralChatMessage] =
//        deriveEncoder
//
//      override def write(entity: ChatMessage.GeneralChatMessage): String =
//        entity.asJson.noSpaces
//      override def read(
//          jsonString: String): Either[String, ChatMessage.GeneralChatMessage] =
//        decode[ChatMessage.GeneralChatMessage](jsonString).left
//          .map(_.getMessage)
//    }
//}
