package com.github.igorramazanov.chat

import java.time

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.testkit.TestKit
import akka.{Done, NotUsed}
import com.dimafeng.testcontainers._
import com.github.igorramazanov.chat.Utils._
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
    imageName = "com.github.igorramazanov/chat:latest",
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
    forAll(Gen.alphaNumStr) { id: String =>
      whenever(id.nonEmpty) {
        val (status, _) = signIn(id, id)
        Forbidden == status
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
    assert(s1 == BadRequest)
  }

  test(
    "it should response with 400 'Bad Request' if /signup with empty id or password") {
    val s1 = signUp("", "NonEmptyString")
    val s2 = signUp("NonEmptyString", "")
    val s3 = signUp("", "")

    assert(s1 == s2)
    assert(s1 == s3)
    assert(s1 == BadRequest)
  }

  test(
    "it should response with 409 'Conflict' if /signup conflicts with existing user") {
    forAll(Gen.alphaNumStr, Gen.alphaNumStr) { (id1: String, id2: String) =>
      whenever(id1.nonEmpty && id2.nonEmpty) {
        val status1 = signUp(id1, id1)
        val status2 = signUp(id1, id1)
        val status3 = signUp(id1, id2)
        status1 == OK && status2 == Conflict && status3 == Conflict
      }
    }
  }

  test(
    "it should response with 101 'Switching protocols' connection on /signin if credentials are right") {
    forAll(Gen.alphaNumStr) { s: String =>
      whenever(s.nonEmpty) {
        val s1 = signUp(s, s)
        val s2 = signIn(s, s)._1
        s1 == OK && s2 == SwitchingProtocols
      }
    }
  }

  test("it should receive messages from other users on connection") {
    forAll(Gen.nonEmptyListOf(Gen.alphaNumChar),
           Gen.nonEmptyListOf(Gen.alphaNumChar),
           Gen.nonEmptyListOf(Gen.nonEmptyListOf(Gen.alphaNumChar))) {
      (id1Raw: List[Char],
       id2Raw: List[Char],
       outgoingMessagesRaw: List[List[Char]]) =>
        val id1 = id1Raw.mkString("")
        val id2 = id2Raw.mkString("")
        val outgoingMessages = outgoingMessagesRaw.map(_.mkString(""))
        signUp(id1, id1)
        signUp(id2, id2)
        val (status1, _) =
          sendAndReceiveMessages(
            id1,
            id1,
            outgoingMessages.map(m => createMessage(id2, m)))
        val (status2, receivedMessages) =
          sendAndReceiveMessages(id2, id2, List.empty)
        status1 == SwitchingProtocols &&
        status2 == SwitchingProtocols &&
        receivedMessages.size == outgoingMessages.size &&
        receivedMessages.zip(outgoingMessages).forall {
          case (received, sent) => received == sent
        }
    }
  }

  def signIn[T](id: String, password: String)(
      implicit
      flow: Flow[Message, Message, T]): (StatusCode, T) = {
    val (f, v) = Http()
      .singleWebSocketRequest(
        WebSocketRequest(
          s"ws://localhost:8080/signin?id=$id&password=$password"),
        flow
      )
    (Await.result(f.map(_.response.status), 5.seconds), v)
  }

  def signUp(id: String, password: String): StatusCode = {
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
      .map(_.status)
    Await.result(f, 5.seconds)
  }

  def sendAndReceiveMessages(
      id: String,
      password: String,
      messages: List[String]): (StatusCode, Seq[String]) = {
    val (status, f) = signIn(id, password)(
      Flow.fromSinkAndSourceMat(
        Flow[Message]
          .mapAsync(1)(m => m.asTextMessage.asScala.toStrict(5.seconds))
          .toMat(Sink.seq)(Keep.right),
        Source(messages).map(TextMessage.apply))(Keep.left))
    (status,
     Await.result(f.map(
                    _.map(
                      _.text.parseJson.asJsObject
                        .fields("payload")
                        .asInstanceOf[JsString]
                        .value)),
                  5.seconds))
  }

  def createMessage(to: String, payload: String): String =
    JsObject("to" -> JsString(to), "payload" -> JsString(payload)).compactPrint
}
