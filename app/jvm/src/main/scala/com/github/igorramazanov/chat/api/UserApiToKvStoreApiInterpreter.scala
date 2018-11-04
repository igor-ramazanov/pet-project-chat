package com.github.igorramazanov.chat.api

import cats.Functor
import cats.syntax.all._
import cats.instances.string._
import com.github.igorramazanov.chat.domain.User
import com.github.igorramazanov.chat.json.DomainEntitiesJsonSupport

object UserApiToKvStoreApiInterpreter {

  implicit def userApiToKvStoreApi[F[_]: Functor](
      implicit kvStoreApi: KvStoreApi[String, String, F],
      jsonSupport: DomainEntitiesJsonSupport): UserApi[F] =
    new UserApi[F] {
      import DomainEntitiesJsonSupport._
      import jsonSupport._

      override def `match`(id: User.Id,
                           email: User.Email,
                           password: User.Password): F[Option[User]] = {
        for {
          rawJson <- kvStoreApi.get(id.value)
        } yield
          rawJson
            .flatMap { jsonString =>
              val u = jsonString.toUser.toOption
              println(u)
              u
            }
            .filter { u =>
              u.password.value === password.value &&
                u.email.value === email.value
            }
      }

      override def save(user: User): F[Either[UserAlreadyExists.type, Unit]] = {
        kvStoreApi
          .setIfEmpty(user.id.value, user.toJson)
          .map(if (_) ().asRight[UserAlreadyExists.type]
          else UserAlreadyExists.asLeft[Unit])
      }

      override def exists(id: User.Id): F[Boolean] =
        kvStoreApi.get(id.value).map(_.nonEmpty)
    }
}
