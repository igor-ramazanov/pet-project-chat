package com.github.igorramazanov.chat.interpreter.redis

import cats.effect.{Async, Timer}
import cats.syntax.all._
import com.github.igorramazanov.chat.api.PersistenceMessagesApi
import com.github.igorramazanov.chat.json.DomainEntitiesJsonSupport
import com.github.igorramazanov.chat.Utils._
import com.github.igorramazanov.chat.domain.ChatMessage._
import org.slf4j.LoggerFactory
import scredis._

import scala.concurrent.ExecutionContext
import com.github.igorramazanov.chat.UtilsShared._

class PersistenceMessagesApiRedisInterpreter[F[_]: Async: Timer] private (
    redis: Redis)(implicit ec: ExecutionContext,
                  jsonSupport: DomainEntitiesJsonSupport)
    extends PersistenceMessagesApi[F] {
  private val logger = LoggerFactory.getLogger(this.getClass)
  private val suffix = "-persistence"
  import jsonSupport._
  import DomainEntitiesJsonSupport._

  override def ofUserOrdered(id: String): F[List[GeneralChatMessage]] = {
    liftFromFuture(
      redis.lRange[String](id + suffix),
      logger
        .error(s"Couldn't retrieve persistence message of user '$id'", _))
      .map { jsonStrings =>
        jsonStrings.flatMap { jsonString =>
          val result = jsonString.toGeneralMessage
          result.fold({ error =>
            logger.warn(
              s"Couldn't parse json: $jsonString as GeneralChatMessage, reason: $error")
            Nil
          }, List(_))
        }
      }
  }

  override def save(id: String, message: GeneralChatMessage): F[Unit] = {
    liftFromFuture(redis.rPush(id + suffix, message.toJson),
                   logger.error(s"Couldn't persist message of user '$id'", _))
      .map(_.discard())
  }
}

object PersistenceMessagesApiRedisInterpreter {
  def apply[F[_]: Async: Timer](redis: Redis)(
      implicit ec: ExecutionContext,
      jsonSupport: DomainEntitiesJsonSupport)
    : PersistenceMessagesApiRedisInterpreter[F] =
    new PersistenceMessagesApiRedisInterpreter[F](redis)
}
