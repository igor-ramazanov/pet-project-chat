package com.github.igorramazanov.chat.interpreter.redis

import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy, QueueOfferResult}
import com.github.igorramazanov.chat.api.IncomingMessagesApi
import com.github.igorramazanov.chat.domain.ChatMessage.GeneralChatMessage
import com.github.igorramazanov.chat.json.DomainEntitiesJsonSupport

import com.github.igorramazanov.chat.UtilsShared._
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import scredis._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class IncomingMessagesApiRedisInterpreter private (
    subscriberClient: () => SubscriberClient)(
    implicit
    actorMaterializer: ActorMaterializer,
    ec: ExecutionContext,
    jsonSupport: DomainEntitiesJsonSupport
) extends IncomingMessagesApi {
  private val bufferSize = 100
  private val logger = LoggerFactory.getLogger(this.getClass)

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  override def subscribe(id: String): Publisher[GeneralChatMessage] = {
    import jsonSupport._
    import DomainEntitiesJsonSupport._
    val (queue, source) = Source
      .queue[GeneralChatMessage](bufferSize, OverflowStrategy.backpressure)
      .preMaterialize()
    val subscription: Subscription = {
      case message: PubSubMessage.Message =>
        val json = message
          .readAs[String]
        json.toGeneralMessage match {
          case Left(error) =>
            logger.error(
              s"Couldn't parse json to GeneralChatMessage, json: $json, reason: $error")
          case Right(generalChatMessage) =>
            logger.debug(s"Received message to user '$id': $json")

            queue.offer(generalChatMessage).onComplete {
              case Success(
                  QueueOfferResult.Dropped | QueueOfferResult.QueueClosed) =>
                logger.warn(s"Message to user '$id' was dropped")
              case Failure(ex) =>
                logger.warn(
                  s"Couldn't send message to source queue of user '$id'",
                  ex)
              case _ =>
            }
        }

      case _ => ()
    }

    subscriberClient().subscribe(id)(subscription).discard()
    source.runWith(Sink.asPublisher(false))
  }
}

object IncomingMessagesApiRedisInterpreter {
  def apply(subscriberClient: () => SubscriberClient)(
      implicit
      actorMaterializer: ActorMaterializer,
      ec: ExecutionContext,
      jsonSupport: DomainEntitiesJsonSupport)
    : IncomingMessagesApiRedisInterpreter =
    new IncomingMessagesApiRedisInterpreter(subscriberClient)(actorMaterializer,
                                                              ec,
                                                              jsonSupport)
}
