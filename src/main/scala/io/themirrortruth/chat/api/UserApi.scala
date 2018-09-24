package io.themirrortruth.chat.api
import io.themirrortruth.chat.entity.User
import simulacrum.typeclass

@typeclass trait UserApi[F[_]] {
  def find(id: String, password: String): F[Option[User]]

  def save(user: User): F[Either[String, Unit]]
}
