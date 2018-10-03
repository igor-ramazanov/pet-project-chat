package io.themirrortruth.chat.api
import cats.Functor
import cats.instances.all._
import cats.syntax.all._
import io.themirrortruth.chat.domain.User

object UserApiToKvStoreApiInterpreter {

  implicit def userAlgebraToKvStoreAlgebra[F[_]: Functor](
      implicit kvStoreAlgebra: KvStoreApi[String, String, F]): UserApi[F] =
    new UserApi[F] {
      override def find(id: String, password: String): F[Option[User]] = {
        for {
          passwordOpt <- kvStoreAlgebra.get(id)
        } yield passwordOpt.filter(password === _).map(User(id, _))
      }

      override def save(user: User): F[Either[String, Unit]] = {
        kvStoreAlgebra
          .setIfEmpty(user.id, user.password)
          .map(
            if (_) ().asRight[String]
            else s"User '${user.id}' already exists".asLeft[Unit])
      }
    }
}
