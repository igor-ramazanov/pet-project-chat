package com.github.igorramazanov.chat.route
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.{FromRequestUnmarshaller, Unmarshaller}
import akka.stream.Materializer
import cats.effect.Effect
import cats.syntax.all._
import com.github.igorramazanov.chat.HttpStatusCodes
import com.github.igorramazanov.chat.Utils.ExecuteToFuture
import com.github.igorramazanov.chat.Utils.ExecuteToFuture.ops._
import com.github.igorramazanov.chat.api._
import com.github.igorramazanov.chat.domain.{
  SignUpRequest,
  User,
  ValidSignUpRequest
}
import com.github.igorramazanov.chat.json.DomainEntitiesJsonSupport
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object SignUp {
  private val logger = LoggerFactory.getLogger(getClass)
  private val messageStrictTimeout = 1.minute

  def createRoute[F[_]: UserApi: ExecuteToFuture: Effect: EmailApi](
      gmailVerificationEmailSender: Option[User.Email],
      emailVerificationLinkPrefix: Option[String],
      emailVerificationTimeout: FiniteDuration)(
      implicit jsonSupport: DomainEntitiesJsonSupport): Route = {
    import DomainEntitiesJsonSupport._
    import jsonSupport._

    implicit val userFromRequestUnmarshaller
      : Unmarshaller[HttpRequest, SignUpRequest] =
      new FromRequestUnmarshaller[SignUpRequest] {
        override def apply(value: HttpRequest)(
            implicit ec: ExecutionContext,
            materializer: Materializer): Future[SignUpRequest] = {
          import cats.data.NonEmptyChain._
          import cats.instances.string._
          import cats.syntax.show._
          value.entity
            .toStrict(messageStrictTimeout)
            .flatMap { entity =>
              entity.data.utf8String.toSignUpRequest match {
                case Left(errors) =>
                  val errorsAsString = errors.show

                  Future.failed(new RuntimeException(
                    s"Couldn't unmarshall HttpRequest entity to SignUpRequest case class, entity: $entity, reasons: $errorsAsString"))
                case Right(signUpRequest) => Future.successful(signUpRequest)
              }
            }
        }
      }

    withRequestTimeout(5.minutes) {
      path("signup") {
        post {
          entity(as[SignUpRequest]) { request =>
            request.validate match {
              case Left(invalidSignUpRequest) =>
                complete(
                  HttpResponse(status = StatusCodes.BadRequest,
                               entity =
                                 HttpEntity(MediaTypes.`application/json`,
                                            invalidSignUpRequest.toJson)))
              case Right(validSignUpRequest) =>
                logger.debug(
                  s"Sending verification email process start for user: '${validSignUpRequest.toString}'")

                val signUpEffect = {
                  for {
                    gmail <- gmailVerificationEmailSender
                    linkPrefix <- emailVerificationLinkPrefix
                  } yield {
                    startEmailVerification(validSignUpRequest,
                                           gmail,
                                           linkPrefix,
                                           emailVerificationTimeout)
                  }
                } getOrElse {
                  signUpWithoutEmailVerification(validSignUpRequest)
                }

                onComplete(signUpEffect.unsafeToFuture) {
                  case Success(responseRoute) => responseRoute
                  case Failure(exception) =>
                    logger.error(
                      s"Some error occurred during email verification start process: '${request.email}', reason: ${exception.getMessage}",
                      exception)
                    complete(StatusCodes.InternalServerError)
                }
            }
          }
        }
      }
    }
  }

  private def signUpWithoutEmailVerification[
      F[_]: UserApi: ExecuteToFuture: Effect: EmailApi](
      validSignUpRequest: ValidSignUpRequest) = {
    UserApi[F].save(validSignUpRequest.asUser).map {
      case Right(_) =>
        logger.debug(
          s"Successfully registered new user ${validSignUpRequest.asUser}")
        complete(StatusCode.int2StatusCode(HttpStatusCodes.SignedUp))
      case Left(UserAlreadyExists) =>
        logger.debug(
          s"User with the same email already exists, ${validSignUpRequest.toString}, conflict")
        complete(StatusCode.int2StatusCode(HttpStatusCodes.UserAlreadyExists))
    }
  }

  private def startEmailVerification[
      F[_]: UserApi: ExecuteToFuture: Effect: EmailApi](
      request: ValidSignUpRequest,
      gmailVerificationEmailSender: User.Email,
      emailVerificationLinkPrefix: String,
      emailVerificationTimeout: FiniteDuration) = {
    UserApi[F].exists(request.id).flatMap { doesUserAlreadyExist =>
      if (doesUserAlreadyExist) {
        logger.debug(
          s"User with the same email already exists, $request, conflict")
        complete(StatusCode.int2StatusCode(HttpStatusCodes.UserAlreadyExists)).pure
      } else {
        EmailApi[F]
          .saveRequestWithExpiration(request, emailVerificationTimeout)
          .flatMap(
            EmailApi[F]
              .sendVerificationEmail(emailVerificationLinkPrefix)(
                request.email,
                gmailVerificationEmailSender,
                _))
          .map {
            case Right(_) =>
              logger.debug(
                s"Successfully sent verification email, email: '${request.email}'")
              complete(StatusCode.int2StatusCode(
                HttpStatusCodes.SuccessfullySentVerificationEmail))
            case Left(exception) =>
              logger.error(
                s"Couldn't send verification email: '${request.email}', reason: ${exception.getMessage}",
                exception)
              complete(StatusCodes.InternalServerError)
          }
      }
    }
  }
}
