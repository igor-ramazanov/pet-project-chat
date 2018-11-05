package com.github.igorramazanov.chat.api
import com.github.igorramazanov.chat.domain.{SignUpRequest, User}
import com.github.igorramazanov.chat.domain.User.Email
import simulacrum.typeclass

import scala.concurrent.duration.FiniteDuration

@typeclass trait EmailApi[F[_]] {
  def saveRequestWithExpiration(
      signUpRequest: SignUpRequest,
      duration: FiniteDuration): F[Email.VerificationId]

  def checkRequestIsExpired(emailVerificationId: Email.VerificationId)
    : F[Either[EmailWasNotVerifiedInTime.type, User]]

  def deleteRequest(emailVerificationId: Email.VerificationId): F[Unit]

  def sendVerificationEmail(verificationLinkPrefix: String)(
      to: Email,
      emailVerificationId: Email.VerificationId): F[Either[Throwable, Unit]]
}

case object EmailWasNotVerifiedInTime {
  override def toString: String = s"Email verification is expired"
}
