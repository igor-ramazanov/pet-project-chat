package com.github.igorramazanov.chat
import java.time.{LocalDateTime, ZoneId, ZoneOffset}

import cats.MonadError
import cats.effect.{Async, Timer}
import simulacrum.typeclass
import cats.syntax.all._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Utils {
  private val MaxRetries = 5

  def currentUtcUnixEpochMillis: Long =
    LocalDateTime.now(ZoneId.of("Z")).toEpochSecond(ZoneOffset.UTC)

  private def retryMonadError[F[_], A](fa: F[A])(
      retries: Int)(implicit M: MonadError[F, Throwable], T: Timer[F]): F[A] =
    fa.handleErrorWith {
      case _ if retries > 0 =>
        T.sleep(1.second).flatMap(_ => retryMonadError(fa)(retries - 1))
      case e => M.raiseError(e)
    }

  implicit class AnyOps(val `this`: Any) extends AnyVal {
    @specialized def discard(): Unit = ()
  }

  implicit class MonadErrorAndTimerOps[F[_], A](val `this`: F[A])
      extends AnyVal {
    def withRetries(implicit M: MonadError[F, Throwable], T: Timer[F]): F[A] =
      retryMonadError(`this`)(MaxRetries)
  }

  def liftFromFuture[F[_]: Async: Timer, A](
      f: => Future[A],
      log: Throwable => Unit)(implicit ec: ExecutionContext): F[A] = {
    Async[F]
      .async[A](callback => {
        f.onComplete {
          case Success(a) => callback(a.asRight[Throwable])
          case Failure(e) => callback(e.asLeft[A])
        }
      })
      .withRetries
      .handleErrorWith { e =>
        log(e)
        e.raiseError
      }
  }

  @typeclass trait ExecuteToFuture[F[_]] {
    def unsafeToFuture[A](f: F[A]): Future[A]
  }
}
