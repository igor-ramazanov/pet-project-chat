package com.github.igorramazanov.chat.api
import com.github.igorramazanov.chat.domain.User
import simulacrum.typeclass

@typeclass trait UserApi[F[_]] {
  def `match`(id: User.Id,
              email: User.Email,
              password: User.Password): F[Option[User]]

  def exists(id: User.Id): F[Boolean]

  def save(user: User): F[Either[UserAlreadyExists.type, Unit]]
}

case object UserAlreadyExists {
  override def toString: String = "User already exists"
}
