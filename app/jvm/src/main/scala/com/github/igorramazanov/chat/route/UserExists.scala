package com.github.igorramazanov.chat.route
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, MediaTypes, StatusCode}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.Functor
import cats.data.NonEmptyChain
import cats.syntax.functor._
import com.github.igorramazanov.chat.ResponseCode
import com.github.igorramazanov.chat.Utils.ToFuture
import com.github.igorramazanov.chat.api.UserApi
import com.github.igorramazanov.chat.domain.{InvalidRequest, User}
import com.github.igorramazanov.chat.json.DomainEntitiesJsonSupport
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success}

object UserExists extends AbstractRoute {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def createRoute[F[_]: ToFuture: UserApi: Functor](
      implicit jsonSupport: DomainEntitiesJsonSupport
  ): Route = {
    import DomainEntitiesJsonSupport._
    import jsonSupport._
    get {
      path("exists") {
        parameters("id") { idRaw =>
          User.Id
            .validate(idRaw)
            .fold(
              { validationErrors =>
                logger.debug(
                  s"Validation error for checking user existence $idRaw, $validationErrors"
                )
                complete(
                  HttpResponse(
                    status = StatusCode.int2StatusCode(ResponseCode.ValidationErrors.value),
                    entity = HttpEntity(
                      MediaTypes.`application/json`,
                      InvalidRequest(
                        validationErrors.flatMap(e => NonEmptyChain(e.errorMessage))
                      ).toJson
                    )
                  )
                )
              }, { id =>
                val effect = UserApi[F].exists(id).map { exists =>
                  if (exists) {
                    logger.debug(s"Checking user existence: $id exists")
                    complete(ResponseCode.Ok)
                  } else {
                    logger.debug(s"Checking user existence: $id does not exists")
                    complete(ResponseCode.UserDoesNotExists)
                  }
                }
                onComplete(ToFuture[F].unsafeToFuture(effect)) {
                  case Success(route) => route
                  case Failure(exception) =>
                    logger.error(
                      s"Some error ocurred during user existing checking: '$idRaw', reason: ${exception.getMessage}",
                      exception
                    )
                    complete(ResponseCode.ServerError)
                }
              }
            )
        }
      }
    }
  }
}
