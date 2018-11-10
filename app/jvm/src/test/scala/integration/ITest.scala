package integration
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.testkit.TestKit
import akka.{Done, NotUsed}
import com.dimafeng.testcontainers.{
  FixedHostPortGenericContainer,
  ForEachTestContainer,
  MultipleContainers
}
import com.github.igorramazanov.chat.ResponseCode
import com.github.igorramazanov.chat.UtilsShared._
import com.github.igorramazanov.chat.domain.User
import io.circe.Json
import io.circe.parser._
import io.circe.syntax._
import org.scalacheck.Gen
import org.scalatest.FunSuiteLike
import org.scalatest.Matchers._
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.testcontainers.containers.output.OutputFrame
import org.testcontainers.containers.wait.strategy.{
  WaitStrategy,
  WaitStrategyTarget
}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class ITest
    extends TestKit(ActorSystem("Chat"))
    with FunSuiteLike
    with ForEachTestContainer
    with GeneratorDrivenPropertyChecks {
  private val lowerAlpha: Set[Char] = ('a' to 'z').toSet
  private val special = Set('!', '@', '_', '-')

  private val invalidIdGen = Gen.alphaNumStr
    .map(_.filterNot(lowerAlpha))
    .map(
      s =>
        s -> User.Id
          .validate(s)
          .toEither
          .left
          .get)

  private val validIdGen =
    Gen.alphaLowerStr
      .filter(_.nonEmpty)
      .map(User.Id.validate(_).toEither.right.get)

  private val invalidEmailGen = for {
    s <- Gen.alphaNumStr
    withoutAt = s.filterNot('@' ===)
    validationErrors = User.Email.validate(withoutAt).toEither.left.get
  } yield withoutAt -> validationErrors

  private val validEmailGen = for {
    name <- Gen.alphaNumStr.filter(_.nonEmpty)
    at = "@"
    domain <- Gen.alphaNumStr.filter(_.nonEmpty)
  } yield User.Email.validate(name + at + domain).toEither.right.get

  private val invalidPasswordGen =
    Gen
      .listOf(
        Gen.oneOf(Gen.numChar,
                  Gen.alphaLowerChar,
                  Gen.alphaUpperChar,
                  Gen.oneOf(special.toSeq)))
      .map(_.mkString(""))
      .map(s => s -> User.Password.validate(s))
      .filter(_._2.isInvalid)
      .map {
        case (s, e) => s -> e.toEither.left.get
      }

  private val validPasswordGen = {
    val gen = for {
      digit <- Gen.numChar.map(_.toString)
      lower <- Gen.alphaLowerChar.map(_.toString)
      upper <- Gen.alphaUpperChar.map(_.toString)
      special <- Gen.oneOf(special.toSeq).map(_.toString)
    } yield digit + lower + upper + special

    Gen
      .listOf(gen)
      .map(_.mkString(""))
      .filter(s => s.size >= 10 && s.size <= 128)
      .map(User.Password.validate(_).toEither.right.get)
  }

  private implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher
  private implicit val materializer: ActorMaterializer = ActorMaterializer()

  private lazy val redis =
    FixedHostPortGenericContainer("redis:latest",
                                  exposedContainerPort = 6379,
                                  exposedHostPort = 6379)

  private lazy val app: FixedHostPortGenericContainer =
    FixedHostPortGenericContainer(
      imageName = "com.github.igorramazanov/chat:latest",
      exposedContainerPort = 8080,
      exposedHostPort = 8080,
      waitStrategy = new WaitStrategy {

        override def withStartupTimeout(
            startupTimeout: java.time.Duration): WaitStrategy = ???

        override def waitUntilReady(
            waitStrategyTarget: WaitStrategyTarget): Unit = {
          Thread.sleep(10.seconds.toMillis)
        }
      }
    ).configure { c =>
      c.withLogConsumer((t: OutputFrame) => print(s">>> ${t.getUtf8String}"))
      c.withCommand("--redis-host",
                     redis.containerInfo.getNetworkSettings.getIpAddress)
        .discard()
    }

  override val container = MultipleContainers(redis, app)

  private implicit def flowIgnore[T]: Flow[Message, Message, NotUsed] =
    Flow.fromSinkAndSource(
      Sink.ignore.asInstanceOf[Sink[Message, Future[Done]]],
      Source.empty[Message])

  private def signIn[T](id: String, email: String, password: String)(
      implicit
      flow: Flow[Message, Message, T]): (Int, T) = {
    val (f, v) = Http()
      .singleWebSocketRequest(
        WebSocketRequest(
          s"ws://localhost:8080/signin?id=${id}&password=${password}&email=${email}"),
        flow
      )
    (Await.result(f.map(_.response.status.intValue()), 5.seconds), v)
  }

  private def signUp(id: String, email: String, password: String): Int = {
    val f = Http()
      .singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = s"http://localhost:8080/signup",
          entity = HttpEntity(MediaTypes.`application/json`,
                              Json
                                .obj("id" -> Json.fromString(id),
                                     "password" -> Json.fromString(password),
                                     "email" -> Json.fromString(email))
                                .noSpaces)
        ))
      .map(_.status.intValue())
    Await.result(f, 5.seconds)
  }

  private def sendAndReceiveMessages(
      id: String,
      email: String,
      password: String,
      messages: List[String]): (Int, Seq[String]) = {
    val (status, f) = signIn(id, email, password)(
      Flow.fromSinkAndSourceMat(
        Flow[Message]
          .mapAsync(1)(m => m.asTextMessage.asScala.toStrict(5.seconds))
          .toMat(Sink.seq)(Keep.right),
        Source(messages).map(TextMessage.apply))(Keep.left))

    val rawMessages = f.map(_.map(_.text))
    val extractedPayloads = rawMessages.map(msgs =>
      msgs.map { msg =>
        parse(msg).toOption
          .flatMap(_.hcursor.downField("payload").as[String].toOption)
          .getOrElse("")
    })

    (status.intValue(), Await.result(extractedPayloads, 5.seconds))
  }

  private def createMessage(to: String, payload: String): String =
    Map(
      "to" -> to,
      "payload" -> payload
    ).asJson.noSpaces

  test(
    "it should response with 'InvalidCredentials' if /signin for unexistent account") {
    forAll(validIdGen, validEmailGen, validPasswordGen)(
      (id, email, password) => {
        val (status, _) = signIn(id.value, email.value, password.value)
        status shouldBe ResponseCode.InvalidCredentials.value
      })
  }

  test(
    "it should response with 'ValidationErrors' if /signin with invalid credentials") {
    forAll(invalidIdGen, invalidEmailGen, invalidPasswordGen) {
      case ((invalidId, _), (invalidEmail, _), (invalidPassword, _)) =>
        val (status, _) =
          signIn(invalidId, invalidEmail, invalidPassword)
        status shouldBe ResponseCode.ValidationErrors.value
    }
  }

  test(
    "it should response with 'ValidationErrors' if /signup with invalid credentials") {
    forAll(invalidIdGen, invalidEmailGen, invalidPasswordGen) {
      case ((invalidId, _), (invalidEmail, _), (invalidPassword, _)) =>
        val status =
          signUp(invalidId, invalidEmail, invalidPassword)
        status shouldBe ResponseCode.ValidationErrors.value
    }
  }

  test("it should response with 'Ok' if /signup with valid credentials") {
    forAll(validIdGen, validEmailGen, validPasswordGen) {
      (id, email, password) =>
        val status =
          signUp(id.value, email.value, password.value)
        status shouldBe ResponseCode.Ok.value
    }
  }
}
