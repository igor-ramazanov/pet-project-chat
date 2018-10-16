package io.github.igorramazanov.chat.interpreter.redis

import cats.effect.{Async, Timer}
import cats.syntax.all._
import io.github.igorramazanov.chat.Utils._
import io.github.igorramazanov.chat.api.PersistenceMessagesApi
import io.github.igorramazanov.chat.domain.ChatMessage._
import io.github.igorramazanov.chat.domain.ChatMessageJsonSupport._
import org.slf4j.LoggerFactory
import scredis._
import spray.json._

import scala.concurrent.ExecutionContext

class PersistenceMessagesApiRedisInterpreter[F[_]: Async: Timer] private (
    redis: Redis)(implicit ec: ExecutionContext)
    extends PersistenceMessagesApi[F] {
  private val logger = LoggerFactory.getLogger(this.getClass)
  private val suffix = "-persistence"

  override def ofUserOrdered(id: String): F[List[GeneralChatMessage]] = {
    liftFromFuture(
      redis.lRange[String](id + suffix),
      logger
        .error(s"Couldn't retrieve persistence message of user '$id'", _))
      .map(_.map(_.parseJson.convertTo[GeneralChatMessage]))
  }

  override def save(id: String, message: GeneralChatMessage): F[Unit] = {
    liftFromFuture(redis.rPush(id + suffix, message.toJson.compactPrint),
                   logger.error(s"Couldn't persist message of user '$id'", _))
      .map(_.discard())
  }
}

object PersistenceMessagesApiRedisInterpreter {
  def apply[F[_]: Async: Timer](redis: Redis)(implicit ec: ExecutionContext)
    : PersistenceMessagesApiRedisInterpreter[F] =
    new PersistenceMessagesApiRedisInterpreter[F](redis)
}
