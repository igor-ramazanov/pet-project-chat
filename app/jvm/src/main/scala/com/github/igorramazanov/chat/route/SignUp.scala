package com.github.igorramazanov.chat.route
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.{FromRequestUnmarshaller, Unmarshaller}
import akka.stream.Materializer
import cats.syntax.all._
import cats.{Functor, Monad}
import com.github.igorramazanov.chat.ResponseCode
import com.github.igorramazanov.chat.Utils.ToFuture
import com.github.igorramazanov.chat.Utils.ToFuture._
import com.github.igorramazanov.chat.Utils.ToFuture.ops._
import com.github.igorramazanov.chat.api._
import com.github.igorramazanov.chat.config.Config.EmailVerificationConfig
import com.github.igorramazanov.chat.domain.{
  SignUpOrInRequest,
  ValidSignUpOrInRequest
}
import com.github.igorramazanov.chat.json.DomainEntitiesJsonSupport
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object SignUp extends AbstractRoute {
  private val logger               = LoggerFactory.getLogger(getClass)
  private val messageStrictTimeout = 1.minute

  def createRoute[F[_]: UserApi: ToFuture: Monad: EmailApi](
      emailVerificationConfig: Option[EmailVerificationConfig]
  )(implicit jsonSupport: DomainEntitiesJsonSupport): Route = {
    import DomainEntitiesJsonSupport._
    import jsonSupport._

    implicit val userFromRequestUnmarshaller
        : Unmarshaller[HttpRequest, SignUpOrInRequest] =
      new FromRequestUnmarshaller[SignUpOrInRequest] {
        override def apply(
            value: HttpRequest
        )(implicit
            ec: ExecutionContext,
            materializer: Materializer
        ): Future[SignUpOrInRequest] = {
          import cats.data.NonEmptyChain._
          import cats.instances.string._
          import cats.syntax.show._

          value.entity
            .toStrict(messageStrictTimeout)
            .flatMap { entity =>
              entity.data.utf8String.toSignUpRequest match {
                case Left(errors)         =>
                  val errorsAsString = errors.show

                  Future.failed(
                    new RuntimeException(
                      s"Couldn't unmarshall HttpRequest entity to SignUpRequest case class, entity: $entity, reasons: $errorsAsString"
                    )
                  )
                case Right(signUpRequest) => Future.successful(signUpRequest)
              }
            }
        }
      }

    withRequestTimeout(5.minutes) {
      path("signup") {
        post {
          entity(as[SignUpOrInRequest]) { request =>
            request.validate match {
              case Left(invalidRequest)      =>
                complete(
                  HttpResponse(
                    status = StatusCode.int2StatusCode(
                      ResponseCode.ValidationErrors.value
                    ),
                    entity = HttpEntity(
                      MediaTypes.`application/json`,
                      invalidRequest.toJson
                    )
                  )
                )
              case Right(validSignUpRequest) =>
                logger.debug(
                  s"Sending verification email process start for user: '${validSignUpRequest.toString}'"
                )

                val signUpEffect =
                  emailVerificationConfig
                    .map(_ => startEmailVerification(validSignUpRequest))
                    .getOrElse {
                      signUpWithoutEmailVerification(validSignUpRequest)
                    }

                onComplete(signUpEffect.unsafeToFuture) {
                  case Success(responseRoute) => responseRoute
                  case Failure(exception)     =>
                    logger.error(
                      s"Some error occurred during email verification start process: '${request.email}', reason: ${exception.getMessage}",
                      exception
                    )
                    complete(ResponseCode.ServerError)
                }
            }
          }
        }
      }
    }
  }

  private def signUpWithoutEmailVerification[F[
      _
  ]: UserApi: ToFuture: Functor: EmailApi](
      validSignUpRequest: ValidSignUpOrInRequest
  ) =
    UserApi[F].save(validSignUpRequest.asUser).map {
      case Right(_)                =>
        logger.debug(
          s"Successfully registered new user ${validSignUpRequest.asUser}"
        )
        complete(ResponseCode.Ok)
      case Left(UserAlreadyExists) =>
        logger.debug(
          s"User with the same email already exists, ${validSignUpRequest.toString}, conflict"
        )
        complete(ResponseCode.UserAlreadyExists)
    }

  private def startEmailVerification[F[_]: UserApi: ToFuture: Monad: EmailApi](
      request: ValidSignUpOrInRequest
  ) =
    UserApi[F].exists(request.id).flatMap { doesUserAlreadyExist =>
      if (doesUserAlreadyExist) {
        logger
          .debug(s"User with the same email already exists, $request, conflict")
        complete(ResponseCode.UserAlreadyExists).pure
      } else
        EmailApi[F]
          .saveRequestWithExpiration(request)
          .flatMap(
            EmailApi[F]
              .sendVerificationEmail(request.email, _)
          )
          .map {
            case Right(_)        =>
              logger.debug(
                s"Successfully sent verification email, email: '${request.email}'"
              )
              complete(ResponseCode.SuccessfullySentVerificationEmail)
            case Left(exception) =>
              logger.error(
                s"Couldn't send verification email: '${request.email}', reason: ${exception.getMessage}",
                exception
              )
              complete(ResponseCode.ServerError)
          }
    }
}
