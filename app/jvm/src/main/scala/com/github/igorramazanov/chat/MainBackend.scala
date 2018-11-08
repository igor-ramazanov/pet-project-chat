package com.github.igorramazanov.chat

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import cats.effect.Effect
import com.github.igorramazanov.chat.Utils.ExecuteToFuture
import com.github.igorramazanov.chat.UtilsShared._
import com.github.igorramazanov.chat.api.UserApiToKvStoreApiInterpreter._
import com.github.igorramazanov.chat.api._
import com.github.igorramazanov.chat.domain.User.Email
import com.github.igorramazanov.chat.interpreter.gmail.EmailApiToKvStoreGmailInterpreter._
import com.github.igorramazanov.chat.interpreter.redis.RedisInterpreters
import com.github.igorramazanov.chat.json.{
  DomainEntitiesCirceJsonSupport,
  DomainEntitiesJsonSupport
}
import com.github.igorramazanov.chat.route._
import monix.eval.Task
import monix.execution.Scheduler
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object MainBackend {

  private lazy val logger = LoggerFactory.getLogger(this.getClass)
  private val shutdownTimeout = 1.minute

  def main(args: Array[String]): Unit = {
    type EffectMonad[+A] = Task[A]

    Config.parser.parse(args, Config.empty).foreach { config =>
      setLogLevel(config.logLevel)
      printJvmInfo()
      printAcceptedConfig(config)

      implicit val actorSystem: ActorSystem = ActorSystem()
      implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
      implicit val scheduler: Scheduler = Scheduler(actorSystem.dispatcher)
      implicit val executeToFuture: ExecuteToFuture[EffectMonad] =
        new ExecuteToFuture[EffectMonad] {
          override def unsafeToFuture[A](f: EffectMonad[A]): Future[A] =
            f.runAsync
        }
      implicit val jsonSupport: DomainEntitiesJsonSupport =
        DomainEntitiesCirceJsonSupport

      val interpreters = RedisInterpreters.redis[EffectMonad](config.redisHost)
      import interpreters._

      val eventualBinding =
        Http().bindAndHandle(
          constructRoutes(config.gmailVerificationEmailSender,
                          config.emailVerificationLinkPrefix,
                          config.emailVerificationTimeout),
          "0.0.0.0",
          8080)
      eventualBinding.foreach(_ =>
        logger.info("Server is listening on 8080 port"))

      sys
        .addShutdownHook {
          Await
            .result(eventualBinding.flatMap(_.unbind()), shutdownTimeout)
            .discard()
        }
        .discard()
    }
  }

  private def printJvmInfo(): Unit = {
    val processorsMessage =
      s"Available processors: ${Runtime.getRuntime.availableProcessors()}."
    val maxMemoryMessage =
      s"Max memory: ${Runtime.getRuntime.maxMemory() / 1024 / 1024} MB"
    logger.info(processorsMessage + " " + maxMemoryMessage)
  }

  private def printAcceptedConfig(config: Config): Unit = {
    logger.info(s"Accepted config:\n${config.toString}")
  }

  private def setLogLevel(logLevel: String): Unit = {
    sys.props.update("LOG_LEVEL", logLevel)
  }

  private def constructRoutes[
      F[_]: ExecuteToFuture: Effect: UserApi: EmailApi: OutgoingMessagesApi: PersistenceMessagesApi](
      gmailVerificationEmailSender: Option[Email],
      emailVerificationLinkPrefix: Option[String],
      emailVerificationTimeout: FiniteDuration)(
      implicit materializer: ActorMaterializer,
      jsonSupport: DomainEntitiesJsonSupport,
      IncomingApi: IncomingMessagesApi
  ): Route = {
    val routesWithoutVerify = SignIn.createRoute ~
      SignUp.createRoute(gmailVerificationEmailSender,
                         emailVerificationLinkPrefix,
                         emailVerificationTimeout) ~
      Status.createRoute ~
      StaticFiles.createRoute ~
      UserExists.createRoute

    Verify
      .createRoute(
        gmailVerificationEmailSender.nonEmpty && emailVerificationLinkPrefix.nonEmpty,
        emailVerificationTimeout)
      .map(_ ~ routesWithoutVerify)
      .getOrElse(routesWithoutVerify)
  }
}
