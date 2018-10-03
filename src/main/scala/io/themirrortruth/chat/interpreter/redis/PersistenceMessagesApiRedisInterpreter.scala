package io.themirrortruth.chat.interpreter.redis

import cats.effect.{Async, Timer}
import cats.syntax.all._
import io.themirrortruth.chat.Utils._
import io.themirrortruth.chat.domain.ChatMessage._
import io.themirrortruth.chat.domain.ChatMessageJsonSupport._
import io.themirrortruth.chat.domain.User
import io.themirrortruth.chat.api.PersistenceMessagesApi
import org.slf4j.LoggerFactory
import scredis._
import spray.json._

import scala.concurrent.ExecutionContext

class PersistenceMessagesApiRedisInterpreter[F[_]: Async: Timer] private (
    redis: Redis)(implicit ec: ExecutionContext)
    extends PersistenceMessagesApi[F] {
  private val logger = LoggerFactory.getLogger(this.getClass)

  override def ofUserOrdered(user: User): F[List[GeneralChatMessage]] = {
    liftFromFuture(
      redis.lRange[String](user.id),
      logger
        .error(s"Couldn't retrieve persistence message of user '${user.id}'",
               _))
      .map(_.map(_.parseJson.convertTo[GeneralChatMessage]))
  }

  override def save(user: User, message: GeneralChatMessage): F[Unit] = {
    liftFromFuture(
      redis.rPush(user.id, message.toJson.compactPrint),
      logger.error(s"Couldn't persist message of user '${user.id}'", _))
      .map(_.discard())
  }
}

object PersistenceMessagesApiRedisInterpreter {
  def apply[F[_]: Async: Timer](redis: Redis)(implicit ec: ExecutionContext)
    : PersistenceMessagesApiRedisInterpreter[F] =
    new PersistenceMessagesApiRedisInterpreter[F](redis)
}
