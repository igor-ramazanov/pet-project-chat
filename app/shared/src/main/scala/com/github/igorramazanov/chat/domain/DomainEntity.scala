package com.github.igorramazanov.chat.domain

import cats.data.Validated._
import cats.data.{NonEmptyChain, ValidatedNec}
import cats.implicits._
import com.github.igorramazanov.chat.UtilsShared._
import com.github.igorramazanov.chat.validation.DomainEntityValidationError.ValidationResult
import com.github.igorramazanov.chat.validation.{
  DomainEntityValidationError,
  EmailValidationError,
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

final case class SignUpRequest(id: String, password: String, email: String)
    extends DomainEntity {
  import cats.syntax.either._

  def validateToUser: Either[InvalidSignUpRequest, User] =
    User
      .safeCreate(id, password, email)
      .toEither
      .leftMap(validationErrors =>
        InvalidSignUpRequest(validationErrors.flatMap(e =>
          NonEmptyChain(e.errorMessage))))

  def unsafeToUser: User =
    User.unsafeCreate(id, password, email)
}

final case class InvalidSignUpRequest(validationErrors: NonEmptyChain[String])
    extends DomainEntity

final case class User private (id: User.Id,
                               password: User.Password,
                               email: User.Email)
    extends DomainEntity

object User {
  private[domain] def apply(id: Id, password: Password, email: Email): User =
    new User(id, password, email)

  final case class Id private[domain] (value: String) extends AnyVal

  object Id {
    private[domain] def apply(value: String): Id = new Id(value)

    private def validateContainsOnlyLowercaseLatinCharacters(
        id: String): ValidationResult[String] =
      if (id.forall(lowercase)) id.validNec
      else IdValidationError.ContainsNotOnlyLowercaseLatinCharacters.invalidNec

    private def validateNonEmpty(id: String): ValidationResult[String] =
      if (id.isEmpty) IdValidationError.IsEmpty.invalidNec else id.validNec

    def validate(id: String): ValidationResult[Id] =
      (validateContainsOnlyLowercaseLatinCharacters(id), validateNonEmpty(id))
        .mapN {
          case _ => Id(id)
        }

    def unsafeCreate(id: String): Id = Id(id)
  }

  final case class Password private[domain] (value: String) extends AnyVal

  object Password {

    private[domain] def apply(value: String): Password = new Password(value)

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

    private def validateContainsLowercaseCharacter(
        password: String): ValidationResult[String] =
      if (!password.exists(lowercase))
        PasswordValidationError.DoesNotContainLowercaseCharacter.invalidNec
      else password.validNec

    private def validateContainsUppercaseCharacter(
        password: String): ValidationResult[String] =
      if (!password.exists(uppercase))
        PasswordValidationError.DoesNotContainUppercaseCharacter.invalidNec
      else password.validNec

    private def validateContainsSpecialCharacter(
        password: String): ValidationResult[String] = {
      if (!password.exists(special))
        PasswordValidationError.DoesNotContainSpecialCharacter.invalidNec
      else password.validNec
    }

    private def validateContainsDigit(
        password: String): ValidationResult[String] =
      if (!password.exists(digits))
        PasswordValidationError.DoesNotContainDigitCharacter.invalidNec
      else password.validNec

    def validate(password: String): ValidationResult[Password] = {
      (validateLengthLess(password),
       validateLengthLonger(password),
       validate2SameAdjacentCharacters(password),
       validateContainsLowercaseCharacter(password),
       validateContainsUppercaseCharacter(password),
       validateContainsDigit(password),
       validateContainsSpecialCharacter(password)).mapN {
        case _ => Password(password)
      }
    }

    def unsafeCreate(password: String): Password = Password(password)
  }

  final case class Email private[domain] (value: String) extends AnyVal

  object Email {
    final case class VerificationId(value: String) extends AnyVal

    private[domain] def apply(value: String): Email = new Email(value)

    private def `validateAtLeast1@Character`(
        email: String): ValidationResult[String] =
      if (email.contains("@")) email.validNec
      else EmailValidationError.`DoesNotContainAtLeast1@Character`.invalidNec

    def validate(email: String): ValidationResult[Email] =
      `validateAtLeast1@Character`(email).map(_ => Email(email))

    def unsafeCreate(email: String): Email = Email(email)
  }

  def safeCreate(
      id: String,
      password: String,
      email: String): ValidatedNec[DomainEntityValidationError, User] = {
    (Id.validate(id), Password.validate(password), Email.validate(email))
      .mapN((id, password, email) => User(id, password, email))
  }

  def safeCreate(id: Id, password: Password, email: Email): User =
    User(id, password, email)

  def unsafeCreate(id: String, password: String, email: String): User =
    new User(Id(id), Password(password), Email(email))
}
