package com.github.igorramazanov.chat.route
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.effect.Effect
import com.github.igorramazanov.chat.HttpStatusCodes
import com.github.igorramazanov.chat.Utils.ExecuteToFuture
import com.github.igorramazanov.chat.Utils.ExecuteToFuture.ops._
import com.github.igorramazanov.chat.api.{
  EmailApi,
  EmailWasNotVerifiedInTime,
  UserAlreadyExists,
  UserApi
}
import com.github.igorramazanov.chat.domain.User
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

object Verify {
  private val logger = LoggerFactory.getLogger(getClass)

  def createRoute[F[_]: EmailApi: UserApi: Effect: ExecuteToFuture](
      isEnabled: Boolean,
      emailVerificationTimeout: FiniteDuration): Option[Route] =
    if (isEnabled) {
      Some(path("verify" / Segment) { rawVerificationId =>
        if (rawVerificationId.nonEmpty) {
          val emailVerificationId = User.Email.VerificationId(rawVerificationId)
          import cats.syntax.all._

          val verificationEndProcess =
            EmailApi[F].checkRequestIsExpired(emailVerificationId).flatMap {
              case Right(request) =>
                UserApi[F].save(request.asUser).map {
                  case Right(_) =>
                    EmailApi[F]
                      .deleteRequest(emailVerificationId)
                      .unsafeToFuture
                    logger.debug(
                      s"Successfully verified user: ${request.toString}")
                    complete(
                      StatusCode.int2StatusCode(HttpStatusCodes.SignedUp))
                  case Left(UserAlreadyExists) =>
                    EmailApi[F]
                      .deleteRequest(emailVerificationId)
                      .unsafeToFuture
                    logger.debug(
                      s"User with such id already exists: ${request.toString}")
                    complete(StatusCode.int2StatusCode(
                      HttpStatusCodes.UserAlreadyExists))
                }
              case Left(EmailWasNotVerifiedInTime) =>
                logger.debug(
                  s"Email was not verified in specified time of $emailVerificationTimeout, verification id: $rawVerificationId")
                complete(
                  StatusCode.int2StatusCode(
                    HttpStatusCodes.EmailWasNotVerifiedInTime)).pure
            }

          onComplete(verificationEndProcess.unsafeToFuture) {
            case Success(responseRoute) => responseRoute
            case Failure(exception) =>
              logger.error(
                s"Some error ocurred during email verification end process: '$rawVerificationId', reason: ${exception.getMessage}",
                exception)
              complete(StatusCodes.InternalServerError)
          }
        } else {
          complete(StatusCode.int2StatusCode(HttpStatusCodes.ValidationErrors))
        }
      })
    } else {
      None
    }
}
