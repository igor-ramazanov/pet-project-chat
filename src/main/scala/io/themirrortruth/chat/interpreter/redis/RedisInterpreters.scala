package io.themirrortruth.chat.interpreter.redis
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.effect.{Async, Timer}
import io.themirrortruth.chat.InterpretersInstances
import io.themirrortruth.chat.api.{
  IncomingMessagesApi,
  KvStoreApi,
  OutgoingMesssagesApi,
  PersistenceMessagesApi
}
import scredis.Redis

import scala.concurrent.ExecutionContext

object RedisInterpreters {
  implicit def redis[F[_]: Async: Timer](
      implicit
      host: String,
      actorSystem: ActorSystem,
      actorMaterializer: ActorMaterializer,
      ec: ExecutionContext
  ): InterpretersInstances[F] = {
    val redis = Redis.withActorSystem(host = host)

    new InterpretersInstances[F] {
      val kvStoreApi: KvStoreApi[String, String, F] =
        KvStoreApiRedisInterpreter[F](redis)
      val outgoingApi: OutgoingMesssagesApi[F] =
        OutgoingMessagesApiRedisInterpreter[F](redis)
      val incomingApi: IncomingMessagesApi =
        IncomingMessagesApiRedisInterpreter(redis)
      val persistenceApi: PersistenceMessagesApi[F] =
        PersistenceMessagesApiRedisInterpreter[F](redis)
    }
  }
}
