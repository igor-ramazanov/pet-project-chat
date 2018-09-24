package io.themirrortruth.chat.interpreter.redis

import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy, QueueOfferResult}
import io.themirrortruth.chat.Utils._
import io.themirrortruth.chat.api._
import io.themirrortruth.chat.entity._
import io.themirrortruth.chat.entity.ChatMessage._
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import scredis._
import spray.json._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class IncomingMessagesApiRedisInterpreter private (redis: Redis)(
    implicit
    actorMaterializer: ActorMaterializer,
    ec: ExecutionContext)
    extends IncomingMessagesApi {
  private val bufferSize = 100
  private val logger = LoggerFactory.getLogger(this.getClass)

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  override def subscribe(user: User): Publisher[GeneralChatMessage] = {
    val (queue, source) = Source
      .queue[GeneralChatMessage](bufferSize, OverflowStrategy.backpressure)
      .preMaterialize()
    val subscription: Subscription = {
      case message: PubSubMessage.Message =>
        val generalChatMessage = message
          .readAs[String]
          .parseJson
          .convertTo[GeneralChatMessage]

        queue.offer(generalChatMessage).onComplete {
          case Success(
              QueueOfferResult.Dropped | QueueOfferResult.QueueClosed) =>
            logger.warn(s"Message to user '${user.id}' was dropped")
          case Failure(ex) =>
            logger.error(
              s"Couldn't send message to source queue of user '${user.id}'",
              ex)
          case _ =>
        }

      case _ => ()
    }

    redis.subscriber.subscribe(user.id)(subscription).discard()
    source.runWith(Sink.asPublisher(false))
  }
}

object IncomingMessagesApiRedisInterpreter {
  def apply(redis: Redis)(
      implicit
      actorMaterializer: ActorMaterializer,
      ec: ExecutionContext): IncomingMessagesApiRedisInterpreter =
    new IncomingMessagesApiRedisInterpreter(redis)(actorMaterializer, ec)
}
