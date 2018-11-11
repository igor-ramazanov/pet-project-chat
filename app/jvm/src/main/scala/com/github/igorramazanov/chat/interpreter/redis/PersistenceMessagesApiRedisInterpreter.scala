package com.github.igorramazanov.chat.interpreter.redis

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import cats.Functor
import cats.effect.{Async, Timer}
import cats.syntax.applicative._
import com.github.igorramazanov.chat.Utils._
import com.github.igorramazanov.chat.api.PersistenceMessagesApi
import com.github.igorramazanov.chat.domain.ChatMessage._
import com.github.igorramazanov.chat.domain.User
import com.github.igorramazanov.chat.json.DomainEntitiesJsonSupport
import org.reactivestreams.{Publisher, Subscriber}
import org.slf4j.LoggerFactory
import scredis._

import scala.concurrent.ExecutionContext

class PersistenceMessagesApiRedisInterpreter[
    F[_]: Async: Timer: ExecuteToFuture] private (redis: Redis)(
    implicit
    materializer: ActorMaterializer,
    ec: ExecutionContext,
    jsonSupport: DomainEntitiesJsonSupport)
    extends PersistenceMessagesApi[F] {
  private val logger = LoggerFactory.getLogger(this.getClass)
  private val suffix = "-persistence"
  import DomainEntitiesJsonSupport._
  import jsonSupport._

  override def ofUserOrdered(id: User.Id): F[Publisher[GeneralChatMessage]] = {
    val retriableFetching: F[List[String]] = liftFromFuture(
      redis.lRange[String](id.value + suffix),
      logger
        .error(s"Couldn't retrieve persistence message of user '$id'", _))

    val publisher = Functor[F].map(retriableFetching) { jsonStrings =>
      val messages = jsonStrings.flatMap { jsonString =>
        val result = jsonString.toGeneralMessage
        result.fold({ error =>
          logger.warn(
            s"Couldn't parse json: $jsonString as GeneralChatMessage, reason: $error")
          Nil
        }, List(_))
      }
      Source(messages).runWith(Sink.asPublisher(fanout = true))
    }
    publisher
  }

  override def save(): F[Subscriber[GeneralChatMessage]] = {
    Flow[GeneralChatMessage]
      .flatMapConcat { m =>
        Source
          .fromFuture(
            ExecuteToFuture[F].unsafeToFuture(retriableSaving(m.from, m)))
          .concat(Source.fromFuture(
            ExecuteToFuture[F].unsafeToFuture(retriableSaving(m.to, m))))
      }
      .to(Sink.ignore)
      .runWith(Source.asSubscriber[GeneralChatMessage])
      .pure
  }

  private def retriableSaving(id: User.Id, m: GeneralChatMessage) =
    Functor[F].map(
      liftFromFuture(
        redis.rPush(id.value + suffix, m.toJson),
        logger.error(s"Couldn't persist message of user '$id'", _)))(_ => ())
}

object PersistenceMessagesApiRedisInterpreter {
  def apply[F[_]: Async: Timer: ExecuteToFuture](redis: Redis)(
      implicit
      materializer: ActorMaterializer,
      ec: ExecutionContext,
      jsonSupport: DomainEntitiesJsonSupport)
    : PersistenceMessagesApiRedisInterpreter[F] =
    new PersistenceMessagesApiRedisInterpreter[F](redis)
}
