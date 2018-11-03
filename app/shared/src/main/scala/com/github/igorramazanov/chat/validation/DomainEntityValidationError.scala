package com.github.igorramazanov.chat.validation
import cats.data.ValidatedNec

sealed trait DomainEntityValidationError extends Product with Serializable {
  def errorMessage: String
}

object DomainEntityValidationError {
  type ValidationResult[A] = ValidatedNec[DomainEntityValidationError, A]
}

sealed trait IdValidationError extends DomainEntityValidationError

object IdValidationError {
  case object ContainsNotOnlyLowercaseLatinCharacters
      extends IdValidationError {
    override def errorMessage: String =
      "User id should contain only lowercase latin characters"
  }

  case object IsEmpty extends IdValidationError {
    override def errorMessage: String = "Should not be empty"
  }
}

sealed trait PasswordValidationError extends DomainEntityValidationError

object PasswordValidationError {
  case object LessThan10Characters extends PasswordValidationError {
    override def errorMessage: String =
      "Password length should not be less than 10 characters"
  }
  case object LongerThan128Characters extends PasswordValidationError {
    override def errorMessage: String =
      "Password length should not be longer than 128 characters"
  }

  case object Contains2SameAdjacentCharacters extends PasswordValidationError {
    override def errorMessage: String =
      "Password should not contain 2 same adjacent characters"
  }

  case object DoesNotContainLowercaseCharacter extends PasswordValidationError {
    override def errorMessage: String =
      "Password should contain at least 1 lowercase character"
  }

  case object DoesNotContainUppercaseCharacter extends PasswordValidationError {
    override def errorMessage: String =
      "Password should contain at least 1 uppercase character"
  }

  case object DoesNotContainSpecialCharacter extends PasswordValidationError {
    override def errorMessage: String =
      "Password should contain at least 1 special character"
  }

  case object DoesNotContainDigitCharacter extends PasswordValidationError {
    override def errorMessage: String = "Should contain at least 1 digit"
  }
}
