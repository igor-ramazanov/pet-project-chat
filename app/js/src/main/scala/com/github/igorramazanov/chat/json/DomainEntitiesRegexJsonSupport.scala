package com.github.igorramazanov.chat.json
import com.github.igorramazanov.chat.domain.ChatMessage.GeneralChatMessage
import com.github.igorramazanov.chat.domain.{ChatMessage, User}

import scala.util.Try

object DomainEntitiesRegexJsonSupport extends DomainEntitiesJsonSupport {
  override implicit def userJsonApi: JsonApi[User] = new JsonApi[User] {
    override def write(entity: User): String =
      s"""{"id":"${entity.id}","password":"${entity.password}"}"""

    override def read(jsonString: String): Either[String, User] = ???
  }
  override implicit def incomingChatMessageJsonApi
    : JsonApi[ChatMessage.IncomingChatMessage] =
    new JsonApi[ChatMessage.IncomingChatMessage] {
      override def write(entity: ChatMessage.IncomingChatMessage): String =
        s"""{"to":"${entity.to}","payload":"${entity.payload}"}"""

      override def read(
          jsonString: String): Either[String, ChatMessage.IncomingChatMessage] =
        ???
    }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  override implicit def generalChatMessageJsonApi
    : JsonApi[ChatMessage.GeneralChatMessage] =
    new JsonApi[ChatMessage.GeneralChatMessage] {
      def find(regex: String, s: String): Either[Throwable, String] =
        Try {
          regex.r
            .findFirstMatchIn(s)
            .map(m => m.group(1))
            .get
        }.toEither

      override def write(entity: ChatMessage.GeneralChatMessage): String =
        ???

      //TODO terrible and buggy way, but let's leave it as is for a while
      override def read(jsonString: String)
        : Either[String, ChatMessage.GeneralChatMessage] = {
        val either = for {
          from <- find(""".*"from":"([^"]+)".*""", jsonString)
          to <- find(""".*"to":"([^"]+)".*""", jsonString)
          payload <- find(""".*"payload":"([^"]+)".*""", jsonString)
          dateTimeUtcEpochSeconds <- find(
            """.*"dateTimeUtcEpochSeconds":([0-9]+).*""",
            jsonString).flatMap(t => Try(t.toLong).toEither)
        } yield GeneralChatMessage(from, to, payload, dateTimeUtcEpochSeconds)
        either.left.map(_.getMessage)
      }
    }
}
