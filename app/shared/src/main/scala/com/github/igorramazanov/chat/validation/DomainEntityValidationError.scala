package com.github.igorramazanov.chat.validation
import cats.Show
import cats.data.ValidatedNec

sealed trait DomainEntityValidationError extends Product with Serializable {
  def errorMessage: String
}

object DomainEntityValidationError {
  type ValidationResult[A] = ValidatedNec[DomainEntityValidationError, A]

  implicit val show = new Show[DomainEntityValidationError] {
    override def show(t: DomainEntityValidationError): String = t.errorMessage
  }
}

sealed trait IdValidationError extends DomainEntityValidationError

object IdValidationError {
  final case object ContainsNotOnlyLowercaseLatinCharacters extends IdValidationError {
    override def errorMessage: String =
      "Id should contain only lowercase latin characters"
  }

  final case object IsEmpty extends IdValidationError {
    override def errorMessage: String = "Id should not be empty"
  }
}

sealed trait PasswordValidationError extends DomainEntityValidationError

object PasswordValidationError {
  final case object LessThan10Characters extends PasswordValidationError {
    override def errorMessage: String =
      "Password length should not be less than 10 characters"
  }
  final case object LongerThan128Characters extends PasswordValidationError {
    override def errorMessage: String =
      "Password length should not be longer than 128 characters"
  }

  final case object Contains2SameAdjacentCharacters extends PasswordValidationError {
    override def errorMessage: String =
      "Password should not contain 2 same adjacent characters"
  }

  final case object DoesNotContainLowercaseCharacter extends PasswordValidationError {
    override def errorMessage: String =
      "Password should contain at least 1 lowercase character"
  }

  final case object DoesNotContainUppercaseCharacter extends PasswordValidationError {
    override def errorMessage: String =
      "Password should contain at least 1 uppercase character"
  }

  final case object DoesNotContainSpecialCharacter extends PasswordValidationError {
    override def errorMessage: String =
      "Password should contain at least 1 special character"
  }

  final case object DoesNotContainDigitCharacter extends PasswordValidationError {
    override def errorMessage: String =
      "Password should contain at least 1 digit"
  }
}

sealed trait EmailValidationError extends DomainEntityValidationError
object EmailValidationError {
  final case object `DoesNotContainAtLeast1@Character` extends EmailValidationError {
    override def errorMessage: String =
      "Email should contain at least 1 '@' character"
  }
}
