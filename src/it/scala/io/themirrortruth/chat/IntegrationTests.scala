package io.themirrortruth.chat
import java.time

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.ws.{Message, WebSocketRequest}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.testkit.TestKit
import com.dimafeng.testcontainers._
import org.scalatest.FunSuiteLike
import org.slf4j.{Logger, LoggerFactory}
import org.testcontainers.containers.output.OutputFrame
import org.testcontainers.containers.wait.strategy.{
  LogMessageWaitStrategy,
  WaitStrategy,
  WaitStrategyTarget
}

import scala.concurrent.Await
import scala.concurrent.duration._

class IntegrationTests
    extends TestKit(ActorSystem("IntegrationTests"))
    with FunSuiteLike
    with ForEachTestContainer {
  import system.dispatcher
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
    c.withEnv("REDIS_HOST", redis.containerInfo.getNetworkSettings.getIpAddress)
  }

  override val container = MultipleContainers(redis, app)

  implicit def flowIgnore[T]: Flow[Message, Message, NotUsed] =
    Flow.fromSinkAndSource(Sink.foreach[Message](_ => ()),
                           Source.empty[Message])

  test("it should response with 403 if /signin with wrong credentials") {
    assert(signIn("1", "1")._1 == 403)
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

  def signUp(id: String, passwordOpt: Option[String]): Int = {
    val password = passwordOpt.getOrElse(id)
    val f = Http()
      .singleRequest(
        HttpRequest(
          uri = s"http://localhost:8080/signup?id=$id&password=$password"
        ))
      .map(_.status.intValue())
    Await.result(f, 5.seconds)
  }
}
