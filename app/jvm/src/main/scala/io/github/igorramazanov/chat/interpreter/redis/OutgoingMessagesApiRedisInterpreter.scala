package io.github.igorramazanov.chat.interpreter.redis

import cats.effect.{Async, Timer}
import cats.syntax.all._
import io.github.igorramazanov.chat.Utils._
import io.github.igorramazanov.chat.api.OutgoingMesssagesApi
import io.github.igorramazanov.chat.domain.ChatMessage.GeneralChatMessage
import io.github.igorramazanov.chat.domain.ChatMessageJsonSupport._
import org.slf4j.LoggerFactory
import scredis.Redis
import spray.json._

import scala.concurrent.ExecutionContext

class OutgoingMessagesApiRedisInterpreter[F[_]: Async: Timer] private (
    redis: Redis)(implicit ec: ExecutionContext)
    extends OutgoingMesssagesApi[F] {
  private val logger = LoggerFactory.getLogger(this.getClass)

  override def send(generalChatMessage: GeneralChatMessage): F[Unit] = {
    logger.debug(
      s"Sending message from '${generalChatMessage.from}' to '${generalChatMessage.to}'")

    liftFromFuture(
      redis.publish(generalChatMessage.to,
                    generalChatMessage.toJson.compactPrint),
      logger.error(
        s"Couldn't publish message from user '${generalChatMessage.from}' to '${generalChatMessage.to}'",
        _)
    ).map(_.discard())

  }
}

object OutgoingMessagesApiRedisInterpreter {
  def apply[F[_]: Async: Timer](redis: Redis)(
      implicit ec: ExecutionContext): OutgoingMessagesApiRedisInterpreter[F] =
    new OutgoingMessagesApiRedisInterpreter[F](redis)
}
