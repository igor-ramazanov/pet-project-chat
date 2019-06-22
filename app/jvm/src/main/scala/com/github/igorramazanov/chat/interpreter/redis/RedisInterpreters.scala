package com.github.igorramazanov.chat.interpreter.redis

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.effect.{Async, Timer}
import com.github.igorramazanov.chat.InterpretersInstances
import com.github.igorramazanov.chat.Utils.ExecuteToFuture
import com.github.igorramazanov.chat.api._
import com.github.igorramazanov.chat.json.DomainEntitiesJsonSupport
import scredis.{Redis, SubscriberClient}

import scala.concurrent.ExecutionContext

object RedisInterpreters {
  def setupInterpreters[F[_]: Async: Timer: ExecuteToFuture](host: String)(
      implicit
      actorSystem: ActorSystem,
      actorMaterializer: ActorMaterializer,
      ec: ExecutionContext,
      jsonSupport: DomainEntitiesJsonSupport
  ): InterpretersInstances[F] = {
    val redis            = Redis.withActorSystem(host = host)
    val subscriberClient = () => SubscriberClient(host = host)
    new InterpretersInstances[F] {
      val kvStoreApi: KvStoreApi[String, String, F] =
        KvStoreApiRedisInterpreter[F](redis)
      val outgoingApi: RealtimeOutgoingMessagesApi[F] =
        RealtimeOutgoingMessagesApiRedisInterpreter[F](redis)
      val incomingApi: RealtimeIncomingMessagesApi[F] =
        RealtimeIncomingMessagesApiRedisInterpreter(subscriberClient)
      val persistenceApi: PersistenceMessagesApi[F] =
        PersistenceMessagesApiRedisInterpreter[F](redis)
    }
  }
}
