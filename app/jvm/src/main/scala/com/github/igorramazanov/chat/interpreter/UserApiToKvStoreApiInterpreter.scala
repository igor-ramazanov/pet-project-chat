package com.github.igorramazanov.chat.interpreter
import cats.Functor
import cats.syntax.functor._
import cats.syntax.eq._
import cats.syntax.either._
import com.github.igorramazanov.chat.api.{KvStoreApi, UserAlreadyExists, UserApi}
import com.github.igorramazanov.chat.domain.User
import com.github.igorramazanov.chat.json.DomainEntitiesJsonSupport
import org.slf4j.LoggerFactory

object UserApiToKvStoreApiInterpreter {
  private val logger = LoggerFactory.getLogger(getClass)

  implicit def userApiToKvStoreApi[F[_]: Functor](
      implicit kvStoreApi: KvStoreApi[String, String, F],
      jsonSupport: DomainEntitiesJsonSupport
  ): UserApi[F] =
    new UserApi[F] {
      import jsonSupport._
      import DomainEntitiesJsonSupport._
      import User.Implicits._

      override def `match`(
          id: User.Id,
          email: User.Email,
          password: User.Password
      ): F[Option[User]] =
        for {
          rawJson <- kvStoreApi.get(id.value)
        } yield rawJson
          .flatMap { jsonString =>
            jsonString.toUser.toOption
          }
          .filter { u =>
            u.password === password &&
            u.email === email
          }

      override def save(user: User): F[Either[UserAlreadyExists.type, Unit]] =
        kvStoreApi
          .setIfEmpty(user.id.value, user.toJson)
          .map(if (_) {
            logger.debug(s"Successfully saved if empty user: ${user.toString}")
            ().asRight[UserAlreadyExists.type]
          } else {
            logger.debug(s"Couldn't save if empty user: ${user.toString}, already exists")
            UserAlreadyExists.asLeft[Unit]
          })

      override def exists(id: User.Id): F[Boolean] =
        kvStoreApi.get(id.value).map(_.nonEmpty)
    }
}
