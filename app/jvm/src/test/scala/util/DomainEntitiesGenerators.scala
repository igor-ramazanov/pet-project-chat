package util
import com.github.igorramazanov.chat.domain.User
import org.scalacheck.Gen
import org.scalatest.Matchers._

import scala.language.postfixOps

trait DomainEntitiesGenerators {
  protected val lowerAlpha: Set[Char] = ('a' to 'z').toSet
  protected val special               = Set('!', '@', '_', '-')

  protected val validIdGen =
    Gen
      .nonEmptyListOf(Gen.alphaLowerChar)
      .map(_.mkString)
      .map(User.Id.validate(_).toEither)
      .map(_.right.get)

  protected val validPasswordGen = {
    val gen = for {
      digit   <- Gen.numChar.map(_.toString)
      lower   <- Gen.alphaLowerChar.map(_.toString)
      upper   <- Gen.alphaUpperChar.map(_.toString)
      special <- Gen.oneOf(special.toSeq).map(_.toString)
    } yield digit + lower + upper + special

    Gen
      .choose(3, 32)
      .flatMap(n => Gen.listOfN(n, gen).map(_.mkString))
      .map(User.Password.validate(_).toEither)
      .map(_.right.get)
  }

  protected val validEmailGen = {
    val nonEmptyStrGen = Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)
    for {
      name   <- nonEmptyStrGen
      at      = "@"
      domain <- nonEmptyStrGen
      either  = User.Email.validate(name + at + domain).toEither
    } yield either.right.get
  }

  protected val userGen = {
    for {
      id       <- validIdGen
      password <- validPasswordGen
      email    <- validEmailGen
    } yield User.safeCreate(id, password, email)
  }

  protected val invalidIdGen = Gen.alphaNumStr
    .map(_.filterNot(lowerAlpha))
    .map(s =>
      s -> User.Id
        .validate(s)
        .toEither
        .left
        .get
    )

  protected val invalidEmailGen    = for {
    s               <- Gen.alphaNumStr
    withoutAt        = s.filterNot('@' ===)
    validationErrors = User.Email.validate(withoutAt).toEither.left.get
  } yield withoutAt -> validationErrors

  protected val invalidPasswordGen =
    Gen.oneOf(Gen.numStr, Gen.alphaLowerStr, Gen.alphaUpperStr).map { s =>
      s -> User.Password.validate(s).toEither.left.get
    }
}
