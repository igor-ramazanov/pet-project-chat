package com.github.igorramazanov.chat.route
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.{FromRequestUnmarshaller, Unmarshaller}
import akka.stream.Materializer
import cats.effect.Effect
import com.github.igorramazanov.chat.Utils.ExecuteToFuture
import com.github.igorramazanov.chat.Utils.ExecuteToFuture.ops._
import com.github.igorramazanov.chat.api._
import com.github.igorramazanov.chat.domain.{SignUpRequest, User}
import com.github.igorramazanov.chat.json.DomainEntitiesJsonSupport
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object SignUp {
  private val logger = LoggerFactory.getLogger(getClass)
  private val messageStrictTimeout = 1.minute

  def createRoute[F[_]: UserApi: ExecuteToFuture: Effect: EmailApi](
      emailVerificationLinkPrefix: String,
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

    path("signup") {
      post {
        entity(as[SignUpRequest]) { request =>
          request.validateToUser match {
            case Left(invalidSignUpRequest) =>
              complete(
                HttpResponse(status = StatusCodes.BadRequest,
                             entity = HttpEntity(MediaTypes.`application/json`,
                                                 invalidSignUpRequest.toJson)))
            case Right(user) =>
              logger.debug(
                s"Sending verification email process start: '${request.email}'")

              import cats.syntax.all._

              val verificationStartEffect =
                UserApi[F].exists(user.id).flatMap { doesUserAlreadyExist =>
                  if (doesUserAlreadyExist) {
                    logger.debug(
                      s"User with email ${user.email.value} already exists, conflict")
                    complete(StatusCodes.Conflict).pure
                  } else {
                    EmailApi[F]
                      .saveRequestWithExpiration(request,
                                                 emailVerificationTimeout)
                      .flatMap(
                        EmailApi[F]
                          .sendVerificationEmail(emailVerificationLinkPrefix)(
                            User.Email.unsafeCreate(request.email),
                            _))
                      .map {
                        case Right(_) =>
                          logger.debug(
                            s"Successfully sent verification email, email: '${request.email}'")
                          complete(StatusCodes.OK)
                        case Left(exception) =>
                          logger.error(
                            s"Couldn't send verification email: '${request.email}', reason: ${exception.getMessage}",
                            exception)
                          complete(StatusCodes.InternalServerError)
                      }
                  }
                }

              onComplete(verificationStartEffect.unsafeToFuture) {
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
