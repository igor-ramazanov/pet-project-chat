package com.github.igorramazanov.chat.interpreter.redis

import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import cats.Applicative
import com.github.igorramazanov.chat.UtilsShared._
import com.github.igorramazanov.chat.api.RealtimeIncomingMessagesApi
import com.github.igorramazanov.chat.domain.ChatMessage.GeneralChatMessage
import com.github.igorramazanov.chat.domain.User
import com.github.igorramazanov.chat.json.DomainEntitiesJsonSupport
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import scredis._

class RealtimeIncomingMessagesApiRedisInterpreter[F[_]] private (
    subscriberClient: () => SubscriberClient
)(
    implicit
    actorMaterializer: ActorMaterializer,
    jsonSupport: DomainEntitiesJsonSupport,
    A: Applicative[F]
) extends RealtimeIncomingMessagesApi[F] {
  private val bufferSize = 100
  private val logger     = LoggerFactory.getLogger(this.getClass)

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  override def subscribe(id: User.Id): F[Publisher[GeneralChatMessage]] = {
    import DomainEntitiesJsonSupport._
    import jsonSupport._
    val (queue, source) = Source
      .queue[GeneralChatMessage](bufferSize, OverflowStrategy.backpressure)
      .preMaterialize()
    val subscription: Subscription = {
      case message: PubSubMessage.Message =>
        val json = message
          .readAs[String]
        json.toGeneralMessage match {
          case Left(error) =>
            logger.error(s"Couldn't parse json to GeneralChatMessage, json: $json, reason: $error")
          case Right(generalChatMessage) =>
            logger.debug(s"Received message to user '$id': $json")

            queue.offer(generalChatMessage).discard()
        }

      case _ => ()
    }
    subscriberClient().subscribe(id.value)(subscription).discard()
    A.pure(source.runWith(Sink.asPublisher(false)))
  }
}

object RealtimeIncomingMessagesApiRedisInterpreter {
  def apply[F[_]: Applicative](subscriberClient: () => SubscriberClient)(
      implicit
      actorMaterializer: ActorMaterializer,
      jsonSupport: DomainEntitiesJsonSupport
  ): RealtimeIncomingMessagesApiRedisInterpreter[F] =
    new RealtimeIncomingMessagesApiRedisInterpreter(subscriberClient)(
      actorMaterializer,
      jsonSupport,
      implicitly[Applicative[F]]
    )
}
