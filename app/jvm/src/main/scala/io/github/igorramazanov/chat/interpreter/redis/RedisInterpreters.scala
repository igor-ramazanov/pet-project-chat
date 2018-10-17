package io.github.igorramazanov.chat.interpreter.redis
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.effect.{Async, Timer}
import io.github.igorramazanov.chat.InterpretersInstances
import io.github.igorramazanov.chat.api.{
  IncomingMessagesApi,
  KvStoreApi,
  OutgoingMessagesApi,
  PersistenceMessagesApi
}
import io.github.igorramazanov.chat.json.DomainEntitiesJsonSupport
import scredis.{Redis, SubscriberClient}

import scala.concurrent.ExecutionContext

object RedisInterpreters {
  implicit def redis[F[_]: Async: Timer](
      implicit
      host: String,
      actorSystem: ActorSystem,
      actorMaterializer: ActorMaterializer,
      ec: ExecutionContext,
      jsonSupport: DomainEntitiesJsonSupport
  ): InterpretersInstances[F] = {
    val redis = Redis.withActorSystem(host = host)
    val subscriberClient = () => SubscriberClient(host = host)

    new InterpretersInstances[F] {
      val kvStoreApi: KvStoreApi[String, String, F] =
        KvStoreApiRedisInterpreter[F](redis)
      val outgoingApi: OutgoingMessagesApi[F] =
        OutgoingMessagesApiRedisInterpreter[F](redis)
      val incomingApi: IncomingMessagesApi =
        IncomingMessagesApiRedisInterpreter(subscriberClient)
      val persistenceApi: PersistenceMessagesApi[F] =
        PersistenceMessagesApiRedisInterpreter[F](redis)
    }
  }
}
