package com.github.igorramazanov.chat.route
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.effect.Effect
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
      emailVerificationTimeout: FiniteDuration): Route =
    path("verify" / Segment) { rawVerificationId =>
      if (rawVerificationId.nonEmpty) {
        val emailVerificationId = User.Email.VerificationId(rawVerificationId)
        import cats.syntax.all._

        val verificationEndProcess =
          EmailApi[F].markAsVerified(emailVerificationId).flatMap {
            case Right(user) =>
              UserApi[F].save(user).map {
                case Right(_) =>
                  logger.info(
                    s"Successfully verified user, email: ${user.email.value}")
                  complete(StatusCodes.OK)
                case Left(UserAlreadyExists) =>
                  logger.debug(
                    s"User with such email already exists, email: ${user.email.value}")
                  complete(StatusCodes.Conflict)
              }
            case Left(EmailWasNotVerifiedInTime) =>
              logger.debug(
                s"Email was not verified in specified time of $emailVerificationTimeout, verification id: $rawVerificationId")
              complete(StatusCodes.Gone).pure
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
        complete(StatusCodes.BadRequest)
      }
    }
}
