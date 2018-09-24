package io.themirrortruth.chat.interpreter.redis

import cats.effect.{Async, Timer}
import cats.syntax.all._
import io.themirrortruth.chat.Utils._
import io.themirrortruth.chat.api.KvStoreApi
import org.slf4j.LoggerFactory
import scredis.{Condition, Redis}

import scala.concurrent.ExecutionContext

class KvStoreApiRedisInterpreter[F[_]: Async: Timer] private (redis: Redis)(
    implicit ec: ExecutionContext)
    extends KvStoreApi[String, String, F] {
  private val logger = LoggerFactory.getLogger(this.getClass)

  override def get(key: String): F[Option[String]] = {
    liftFromFuture(redis.get(key),
                   logger.error(s"Couldn't 'get' value by key '$key'", _))
  }

  override def set(key: String, value: String): F[Unit] = {
    liftFromFuture(redis.set(key, value),
                   logger.error(s"Couldn't 'set' value by key '$key'", _))
      .map(_.discard())
  }

  override def setIfEmpty(key: String, value: String): F[Boolean] = {
    liftFromFuture(
      redis.set(key, value, conditionOpt = Some(Condition.NX)),
      logger.error(s"Couldn't 'setIfEmpty' value by key '$key'", _))
  }
}

object KvStoreApiRedisInterpreter {
  def apply[F[_]: Async: Timer](redis: Redis)(
      implicit ec: ExecutionContext): KvStoreApiRedisInterpreter[F] =
    new KvStoreApiRedisInterpreter[F](redis)
}
