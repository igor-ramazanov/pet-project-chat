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
          rawJson <- kvStoreApi.get(email.value)
        } yield
          rawJson
            .flatMap { jsonString =>
              jsonString.toUser.toOption
            }
            .filter(u =>
              u.password.value === password.value && u.id.value === id.value)
      }

      override def save(user: User): F[Either[UserAlreadyExists.type, Unit]] = {
        kvStoreApi
          .setIfEmpty(user.id.value, user.password.value)
          .map(if (_) ().asRight[UserAlreadyExists.type]
          else UserAlreadyExists.asLeft[Unit])
      }

      override def exists(email: User.Email): F[Boolean] =
        kvStoreApi.get(email.value).map(_.nonEmpty)
    }
}
