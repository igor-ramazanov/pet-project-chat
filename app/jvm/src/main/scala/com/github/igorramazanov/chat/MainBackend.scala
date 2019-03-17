package com.github.igorramazanov.chat

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import cats.Monad
import cats.effect.{Async, Sync, Timer}
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.github.igorramazanov.chat.Utils._
import com.github.igorramazanov.chat.UtilsShared._
import com.github.igorramazanov.chat.api._
import com.github.igorramazanov.chat.config.Config
import com.github.igorramazanov.chat.domain.User.Email
import com.github.igorramazanov.chat.domain.ValidSignUpOrInRequest
import com.github.igorramazanov.chat.interpreter.EmailApiToKvStoreApiInterpreter
import com.github.igorramazanov.chat.interpreter.redis.RedisInterpreters
import com.github.igorramazanov.chat.json.{
  DomainEntitiesCirceJsonSupport,
  DomainEntitiesJsonSupport
}
import com.github.igorramazanov.chat.route._
import monix.eval.{Task, TaskApp}
import monix.execution.Scheduler
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object MainBackend extends TaskApp {

  private lazy val logger = LoggerFactory.getLogger(this.getClass)
  private val shutdownTimeout = 10.seconds

  override def run(args: Array[String]): Task[Unit] = {
    implicit val actorSystem: ActorSystem = ActorSystem()
    implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
    implicit val scheduler: Scheduler = Scheduler(actorSystem.dispatcher)
    implicit val e: ExecuteToFuture[Task] = new ExecuteToFuture[Task] {
      override def unsafeToFuture[A](f: Task[A]): Future[A] = f.runAsync
    }

    Config.parser
      .parse(args, Config.empty)
      .fold(Task(logger.error(s"Couldn't parse configuration"))) { c =>
        setLogLevel(c.logLevel)
        program(c, shutdownTimeout)
      }
  }

  def program[F[_]: ExecuteToFuture: Async: Timer](config: Config,
                                                   timeout: FiniteDuration)(
      implicit actorSystem: ActorSystem,
      actorMaterializer: ActorMaterializer): F[Unit] = {

    import actorSystem.dispatcher

    implicit val jsonSupport: DomainEntitiesJsonSupport =
      DomainEntitiesCirceJsonSupport

    val interpreters: InterpretersInstances[F] =
      RedisInterpreters.setupInterpreters[F](config.redisHost)
    import com.github.igorramazanov.chat.interpreter.UserApiToKvStoreApiInterpreter.userApiToKvStoreApi
    import interpreters._

    implicit val emailApi: EmailApi[F] =
      config.emailVerificationConfig.map { c =>
        new EmailApiToKvStoreApiInterpreter[F](c)
      } getOrElse {
        val noOp = new EmailApi[F]() {
          override def saveRequestWithExpiration(
              signUpRequest: ValidSignUpOrInRequest): F[Email.VerificationId] =
            ???

          override def checkRequestIsExpired(
              emailVerificationId: Email.VerificationId): F[
            Either[EmailWasNotVerifiedInTime.type, ValidSignUpOrInRequest]] =
            ???
          override def deleteRequest(
              emailVerificationId: Email.VerificationId): F[Unit] =
            ???
          override def sendVerificationEmail(
              to: Email,
              emailVerificationId: Email.VerificationId)
            : F[Either[Throwable, Unit]] = ???
        }
        noOp
      }

    for {
      _ <- printJvmInfo
      _ <- printAcceptedConfig(config)
      b <- bind(config)
      _ <- scheduleOnShutdownHook(actorSystem, b, timeout)
    } yield ()
  }

  private def printJvmInfo[F[_]: Sync]: F[Unit] = Sync[F].delay {
    val processorsMessage =
      s"Available processors: ${Runtime.getRuntime.availableProcessors()}."
    val maxMemoryMessage =
      s"Max memory: ${Runtime.getRuntime.maxMemory() / 1024 / 1024} MB"
    logger.info(processorsMessage + " " + maxMemoryMessage)
  }

  private def printAcceptedConfig[F[_]: Sync](config: Config): F[Unit] =
    Sync[F].delay {
      logger.info(s"Accepted config:\n${config.toString}")
    }

  private def setLogLevel(logLevel: String): Unit =
    sys.props.update("LOG_LEVEL", logLevel)

  private def scheduleOnShutdownHook[F[_]: Sync](
      actorSystem: ActorSystem,
      binding: Http.ServerBinding,
      timeout: FiniteDuration): F[Unit] = {
    import actorSystem.dispatcher
    Sync[F].delay {
      sys
        .addShutdownHook {
          val future = binding
            .terminate(timeout)
            .flatMap(_ => actorSystem.terminate())
          Await.ready(future, timeout).discard()
        }
        .discard()
    }
  }

  private def bind[
      F[_]: ExecuteToFuture: Async: Timer: UserApi: EmailApi: RealtimeOutgoingMessagesApi: PersistenceMessagesApi: RealtimeIncomingMessagesApi](
      config: Config)(implicit actorSystem: ActorSystem,
                      actorMaterializer: ActorMaterializer,
                      domainEntitiesJsonSupport: DomainEntitiesJsonSupport) = {
    import actorSystem.dispatcher
    liftFromFuture(
      {
        Http().bindAndHandle(constructRoutes(config), "0.0.0.0", 8080).map {
          b =>
            logger.info("Server is listening on 8080 port")
            b
        }
      },
      e => logger.error(s"Couldn't bind server, ${e.getMessage}")
    )
  }

  private def constructRoutes[
      F[_]: ExecuteToFuture: Monad: UserApi: EmailApi: RealtimeOutgoingMessagesApi: PersistenceMessagesApi: RealtimeIncomingMessagesApi](
      config: Config)(
      implicit jsonSupport: DomainEntitiesJsonSupport
  ): Route = {
    val routesWithoutVerify = SignIn.createRoute ~
      SignUp.createRoute(config.emailVerificationConfig) ~
      Status.createRoute ~
      StaticFiles.createRoute ~
      UserExists.createRoute

    config.emailVerificationConfig
      .map(c => Verify.createRoute(c.timeout) ~ routesWithoutVerify)
      .getOrElse(routesWithoutVerify)
  }
}
