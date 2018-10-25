package com.github.igorramazanov.chat.api
import com.github.igorramazanov.chat.domain.User
import simulacrum.typeclass

@typeclass trait UserApi[F[_]] {
  def find(id: String, password: String): F[Option[User]]

  def save(user: User): F[Either[String, Unit]]
}
