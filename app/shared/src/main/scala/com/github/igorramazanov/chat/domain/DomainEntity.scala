package com.github.igorramazanov.chat.domain

import cats.data.Validated._
import cats.data.{NonEmptyChain, ValidatedNec}
import cats.implicits._

import com.github.igorramazanov.chat.UtilsShared._
import com.github.igorramazanov.chat.validation.DomainEntityValidationError.ValidationResult
import com.github.igorramazanov.chat.validation.{
  DomainEntityValidationError,
  IdValidationError,
  PasswordValidationError
}

sealed trait DomainEntity extends Product with Serializable

sealed trait ChatMessage extends DomainEntity

object ChatMessage {
  final case class IncomingChatMessage(to: String, payload: String)
      extends ChatMessage {
    def asGeneral(from: User, dateTimeEpochSeconds: Long) =
      GeneralChatMessage(from = from.id.value,
                         to = to,
                         payload = payload,
                         dateTimeUtcEpochSeconds = dateTimeEpochSeconds)
  }
  final case class GeneralChatMessage(from: String,
                                      to: String,
                                      payload: String,
                                      dateTimeUtcEpochSeconds: Long)
      extends ChatMessage
}

sealed trait KeepAliveMessage extends DomainEntity
object KeepAliveMessage {
  final case object Ping extends KeepAliveMessage {
    override def toString: String = "ping"
  }
  final case object Pong extends KeepAliveMessage {
    override def toString: String = "pong"
  }
}

final case class SignUpRequest(id: String, password: String)
    extends DomainEntity {
  import cats.syntax.either._

  def validateToUser: Either[InvalidSignUpRequest, User] =
    User
      .safeCreate(id, password)
      .toEither
      .leftMap(validationErrors =>
        InvalidSignUpRequest(validationErrors.flatMap(e =>
          NonEmptyChain(e.errorMessage))))
}

final case class InvalidSignUpRequest(validationErrors: NonEmptyChain[String])
    extends DomainEntity

final case class User private (id: User.Id, password: User.Password)
    extends DomainEntity

object User {
  final case class Id private[domain] (value: String) extends AnyVal

  object Id {
    private def validateContainsOnlyLowercaseLatinCharacters(
        id: String): ValidationResult[String] =
      if (id.forall(lowercase)) id.validNec
      else IdValidationError.ContainsNotOnlyLowercaseLatinCharacters.invalidNec

    private def validateNonEmpty(id: String): ValidationResult[String] =
      if (id.isEmpty) IdValidationError.IsEmpty.invalidNec else id.validNec

    def validate(id: String): ValidationResult[Id] =
      (validateContainsOnlyLowercaseLatinCharacters(id), validateNonEmpty(id))
        .mapN {
          case _ => new Id(id)
        }
  }

  final case class Password private[domain] (value: String) extends AnyVal

  object Password {

    private def validateLengthLess(password: String): ValidationResult[String] =
      if (password.length < 10)
        PasswordValidationError.LessThan10Characters.invalidNec
      else password.validNec

    private def validateLengthLonger(
        password: String): ValidationResult[String] =
      if (password.length > 128)
        PasswordValidationError.LongerThan128Characters.invalidNec
      else password.validNec

    private def validate2SameAdjacentCharacters(
        password: String): ValidationResult[String] =
      if (password.length >= 2 && password.zip(password.tail).exists {
            case (a, b) => a == b
          })
        PasswordValidationError.Contains2SameAdjacentCharacters.invalidNec
      else password.validNec

    private def validateContainLowercaseCharacter(
        password: String): ValidationResult[String] =
      if (!password.exists(lowercase))
        PasswordValidationError.DoesNotContainLowercaseCharacter.invalidNec
      else password.validNec

    private def validateContainUppercaseCharacter(
        password: String): ValidationResult[String] =
      if (!password.exists(uppercase))
        PasswordValidationError.DoesNotContainUppercaseCharacter.invalidNec
      else password.validNec

    private def validateContainSpecialCharacter(
        password: String): ValidationResult[String] = {
      if (!password.exists(special))
        PasswordValidationError.DoesNotContainSpecialCharacter.invalidNec
      else password.validNec
    }

    private def validateContainDigit(
        password: String): ValidationResult[String] =
      if (!password.exists(digits))
        PasswordValidationError.DoesNotContainDigitCharacter.invalidNec
      else password.validNec

    def validate(password: String): ValidationResult[Password] = {
      (validateLengthLess(password),
       validateLengthLonger(password),
       validate2SameAdjacentCharacters(password),
       validateContainLowercaseCharacter(password),
       validateContainUppercaseCharacter(password),
       validateContainDigit(password),
       validateContainSpecialCharacter(password)).mapN {
        case _ => new Password(password)
      }
    }
  }

  def safeCreate(
      id: String,
      password: String): ValidatedNec[DomainEntityValidationError, User] = {
    (Id.validate(id), Password.validate(password)).mapN((id, password) =>
      new User(id, password))
  }

  def unsafeCreate(id: String, password: String): User =
    new User(new Id(id), new Password(password))
}
