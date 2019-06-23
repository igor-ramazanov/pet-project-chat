package com.github.igorramazanov.chat.interpreter.redis

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import cats.effect.{Async, Timer}
import cats.syntax.functor._
import cats.syntax.applicative._
import com.github.igorramazanov.chat.Utils._
import com.github.igorramazanov.chat.Utils.ToFuture.ops._
import com.github.igorramazanov.chat.UtilsShared._
import com.github.igorramazanov.chat.api.RealtimeOutgoingMessagesApi
import com.github.igorramazanov.chat.domain.{ChatMessage, User}
import com.github.igorramazanov.chat.json.DomainEntitiesJsonSupport
import org.reactivestreams.Subscriber
import org.slf4j.LoggerFactory
import scredis.Redis

import scala.concurrent.{ExecutionContext, Future}

class RealtimeOutgoingMessagesApiRedisInterpreter[F[_]: Async: Timer: ToFuture] private (
    redis: Redis
)(
    implicit
    materializer: ActorMaterializer,
    ec: ExecutionContext,
    jsonSupport: DomainEntitiesJsonSupport
) extends RealtimeOutgoingMessagesApi[F] {
  import DomainEntitiesJsonSupport._
  import jsonSupport._
  private val logger = LoggerFactory.getLogger(this.getClass)

  private def sendWithRetries(to: User.Id, m: String): Future[Unit] =
    liftFromFuture[F, Long](
      redis.publish(to.value, m),
      logger.error(s"Couldn't publish message to user '$to'", _)
    ).map(_.discard()).unsafeToFuture

  override def send(): F[Subscriber[ChatMessage.GeneralChatMessage]] = {

    val subscriber = Flow[ChatMessage.GeneralChatMessage]
      .flatMapConcat { m =>
        logger.debug(s"Sending message from '${m.from}' to '${m.to}'")
        val json = m.toJson
        Source
          .fromFuture(sendWithRetries(m.from, json))
          .concat(Source.fromFuture(sendWithRetries(m.to, json)))
      }
      .to(Sink.ignore)
      .runWith(Source.asSubscriber[ChatMessage.GeneralChatMessage])
    subscriber.pure
  }
}

object RealtimeOutgoingMessagesApiRedisInterpreter {
  def apply[F[_]: Async: Timer: ToFuture](redis: Redis)(
      implicit
      materializer: ActorMaterializer,
      ec: ExecutionContext,
      jsonSupport: DomainEntitiesJsonSupport
  ): RealtimeOutgoingMessagesApiRedisInterpreter[F] =
    new RealtimeOutgoingMessagesApiRedisInterpreter[F](redis)
}
