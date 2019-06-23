package integration
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.{Done, NotUsed}
import com.github.igorramazanov.chat.domain.{ChatMessage, KeepAliveMessage, User}
import com.github.igorramazanov.chat.json.DomainEntitiesCirceJsonSupport
import io.circe.Json
import org.scalacheck.Gen
import org.scalatest.FunSuiteLike
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait ITestHarness extends FunSuiteLike with ScalaCheckDrivenPropertyChecks {

  protected implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher
  protected implicit val materializer: ActorMaterializer = ActorMaterializer()
  protected implicit def flowIgnore[T]: Flow[Message, Message, NotUsed] =
    Flow.fromSinkAndSource(
      Sink.ignore.asInstanceOf[Sink[Message, Future[Done]]],
      Source.empty[Message]
    )
  protected val jsonSupport: DomainEntitiesCirceJsonSupport.type = DomainEntitiesCirceJsonSupport
  import com.github.igorramazanov.chat.json.DomainEntitiesJsonSupport._
  import jsonSupport._

  protected val timeout: FiniteDuration = 15.seconds

  // A generators .sample.get can sometimes return None, but these examples have no reason to not generate a result,
  // so defend against that and retry if it does happen.
  protected def sample[T](g: Gen[T]): T = {
    def loop(i: Int): T = {
      assert(i > 0, "Should be able to generate a sample.")
      g.sample.fold(loop(i - 1))(identity)
    }
    loop(10)
  }

  protected def signIn[T](user: User)(
      implicit
      flow: Flow[Message, Message, T]
  ): (Int, T) =
    signIn[T](user.id.value, user.email.value, user.password.value)

  protected def signIn[T](id: String, email: String, password: String)(
      implicit
      flow: Flow[Message, Message, T]
  ): (Int, T) = {
    val (f, v) = Http()
      .singleWebSocketRequest(
        WebSocketRequest(
          s"ws://localhost:8080/signin?id=$id&password=$password&email=$email"
        ),
        flow
      )
    (Await.result(f.map(_.response.status.intValue()), timeout), v)
  }

  protected def signUp(user: User): Int =
    signUp(user.id.value, user.email.value, user.password.value)

  protected def signUp(id: String, email: String, password: String): Int = {
    val f = Http()
      .singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = s"http://localhost:8080/signup",
          entity = HttpEntity(
            MediaTypes.`application/json`,
            Json
              .obj(
                "id"       -> Json.fromString(id),
                "password" -> Json.fromString(password),
                "email"    -> Json.fromString(email)
              )
              .noSpaces
          )
        )
      )
      .map(_.status.intValue())
    Await.result(f, timeout)
  }

  protected def sendAndReceiveMessages(
      from: User,
      messages: List[ChatMessage.IncomingChatMessage]
  ): (Int, Seq[ChatMessage.GeneralChatMessage]) = {
    val (status, f) = signIn(from)(
      Flow.fromSinkAndSourceMat(
        Flow[Message]
          .mapAsync(1)(m => m.asTextMessage.asScala.toStrict(timeout))
          .toMat(Sink.seq)(Keep.right),
        Source(messages).map(m => TextMessage(m.toJson))
      )(Keep.left)
    )

    val rawMessages = f.map(
      _.map(_.text).filterNot(
        m => m === KeepAliveMessage.Ping.toString || m === KeepAliveMessage.Pong.toString
      )
    )
    val extractedPayloads = rawMessages.map(
      msgs =>
        msgs.map { msg =>
          val message = msg.toGeneralMessage
          message.right.get
        }
    )

    (status.intValue(), Await.result(extractedPayloads, timeout))
  }
}
