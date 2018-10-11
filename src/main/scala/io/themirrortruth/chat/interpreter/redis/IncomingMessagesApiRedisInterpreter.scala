package io.themirrortruth.chat.interpreter.redis

import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy, QueueOfferResult}
import io.themirrortruth.chat.api._
import io.themirrortruth.chat.domain.ChatMessage._
import io.themirrortruth.chat.domain.ChatMessageJsonSupport._
import io.themirrortruth.chat.Utils._
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import scredis._
import spray.json._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class IncomingMessagesApiRedisInterpreter private (
    subscriberClient: () => SubscriberClient)(
    implicit
    actorMaterializer: ActorMaterializer,
    ec: ExecutionContext)
    extends IncomingMessagesApi {
  private val bufferSize = 100
  private val logger = LoggerFactory.getLogger(this.getClass)

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  override def subscribe(id: String): Publisher[GeneralChatMessage] = {
    val (queue, source) = Source
      .queue[GeneralChatMessage](bufferSize, OverflowStrategy.backpressure)
      .preMaterialize()
    val subscription: Subscription = {
      case message: PubSubMessage.Message =>
        val generalChatMessage = message
          .readAs[String]
          .parseJson
          .convertTo[GeneralChatMessage]

        logger.debug(
          s"Received message to user '$id': ${generalChatMessage.toJson.compactPrint}")

        queue.offer(generalChatMessage).onComplete {
          case Success(
              QueueOfferResult.Dropped | QueueOfferResult.QueueClosed) =>
            logger.warn(s"Message to user '$id' was dropped")
          case Failure(ex) =>
            logger.error(s"Couldn't send message to source queue of user '$id'",
                         ex)
          case _ =>
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
      ec: ExecutionContext): IncomingMessagesApiRedisInterpreter =
    new IncomingMessagesApiRedisInterpreter(subscriberClient)(actorMaterializer,
                                                              ec)
}
