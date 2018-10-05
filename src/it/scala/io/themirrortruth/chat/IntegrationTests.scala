package io.themirrortruth.chat
import java.time

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{Message, WebSocketRequest}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.testkit.TestKit
import akka.{Done, NotUsed}
import com.dimafeng.testcontainers._
import io.themirrortruth.chat.IntegrationTestUtils._
import org.scalacheck.Gen
import org.scalatest.FunSuiteLike
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.slf4j.{Logger, LoggerFactory}
import org.testcontainers.containers.output.OutputFrame
import org.testcontainers.containers.wait.strategy.{
  WaitStrategy,
  WaitStrategyTarget
}
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class IntegrationTests
    extends TestKit(ActorSystem("Chat"))
    with FunSuiteLike
    with ForEachTestContainer
    with GeneratorDrivenPropertyChecks {

  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  lazy val redis =
    FixedHostPortGenericContainer("redis:latest",
                                  exposedContainerPort = 6379,
                                  exposedHostPort = 6379)

  lazy val app: FixedHostPortGenericContainer = FixedHostPortGenericContainer(
    imageName = "io.themirrortruth/chat:latest",
    exposedContainerPort = 8080,
    exposedHostPort = 8080,
    waitStrategy = new WaitStrategy {

      override def withStartupTimeout(
          startupTimeout: time.Duration): WaitStrategy = ???

      override def waitUntilReady(
          waitStrategyTarget: WaitStrategyTarget): Unit = {
        Thread.sleep(5.seconds.toMillis)
      }
    }
  ).configure { c =>
    c.withLogConsumer((t: OutputFrame) => println(s">>> ${t.getUtf8String}"))
    c.withEnv("REDIS_HOST",
               redis.containerInfo.getNetworkSettings.getIpAddress)
      .discard()
  }

  override val container = MultipleContainers(redis, app)

  implicit def flowIgnore[T]: Flow[Message, Message, NotUsed] =
    Flow.fromSinkAndSource(
      Sink.ignore.asInstanceOf[Sink[Message, Future[Done]]],
      Source.empty[Message])

  implicit def intToStatusCode(i: Int): StatusCode =
    StatusCode.int2StatusCode(i)

  test(
    "it should response with 403 'Forbidden' if /signin with wrong credentials") {
    forAll(Gen.alphaNumStr) { s: String =>
      whenever(s.nonEmpty) {
        val (status, _) = signIn(s, s)
        Forbidden.intValue == status
      }
    }
  }

  test(
    "it should response with 400 'Bad Request' if /signin with empty id or password") {
    val (s1, _) = signIn("", "NonEmptyString")
    val (s2, _) = signIn("NonEmptyString", "")
    val (s3, _) = signIn("", "")

    assert(s1 == s2)
    assert(s1 == s3)
    assert(s1 == BadRequest.intValue)
  }

  test(
    "it should response with 400 'Bad Request' if /signup with empty id or password") {
    val s1 = signUp("", "NonEmptyString")
    val s2 = signUp("NonEmptyString", "")
    val s3 = signUp("", "")

    assert(s1 == s2)
    assert(s1 == s3)
    assert(s1 == BadRequest.intValue)
  }

  test(
    "it should response with 409 'Conflict' if /signup conflicts with existing user") {
    forAll { (s1: String, s2: String) =>
      whenever(s1.nonEmpty && s2.nonEmpty) {
        val status1 = signUp(s1, s1)
        val status2 = signUp(s1, s1)
        val status3 = signUp(s1, s2)
        status1 == OK.intValue && status2 == Conflict.intValue && status3 == Conflict.intValue
      }
    }
  }

  test(
    "it should response with 101 'Switching protocols' connection on /signin if credentials are right") {
    forAll(Gen.alphaNumStr) { s: String =>
      whenever(s.nonEmpty) {
        val s1 = signUp(s, s)
        val s2 = signIn(s, s)._1
        s1 === OK && s2 === SwitchingProtocols
      }
    }
  }

  def signIn[T](id: String, password: String)(
      implicit
      flow: Flow[Message, Message, T]): (Int, T) = {
    val (f, v) = Http()
      .singleWebSocketRequest(
        WebSocketRequest(
          s"ws://localhost:8080/signin?id=$id&password=$password"),
        flow
      )
    (Await.result(f.map(_.response.status.intValue()), 5.seconds), v)
  }

  def signUp(id: String, password: String): Int = {
    val f = Http()
      .singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = s"http://localhost:8080/signup",
          entity =
            HttpEntity(MediaTypes.`application/json`,
                       JsObject("id" -> JsString(id),
                                "password" -> JsString(password)).compactPrint)
        ))
      .map(_.status.intValue())
    Await.result(f, 5.seconds)
  }

  def sendMessages(id: String,
                   password: String,
                   messages: Seq[String]): List[String] = ???

  def createMessage(to: String, payload: String): String =
    JsObject("to" -> JsString(to), "payload" -> JsString(payload)).compactPrint
}
