package com.github.igorramazanov.chat.api
import com.github.igorramazanov.chat.domain.User.Email
import com.github.igorramazanov.chat.domain.ValidSignUpRequest
import simulacrum.typeclass

@typeclass trait EmailApi[F[_]] {
  def saveRequestWithExpiration(
      signUpRequest: ValidSignUpRequest): F[Email.VerificationId]

  def checkRequestIsExpired(emailVerificationId: Email.VerificationId)
    : F[Either[EmailWasNotVerifiedInTime.type, ValidSignUpRequest]]

  def deleteRequest(emailVerificationId: Email.VerificationId): F[Unit]

  def sendVerificationEmail(
      to: Email,
      emailVerificationId: Email.VerificationId): F[Either[Throwable, Unit]]
}

case object EmailWasNotVerifiedInTime {
  override def toString: String = s"Email verification is expired"
}
