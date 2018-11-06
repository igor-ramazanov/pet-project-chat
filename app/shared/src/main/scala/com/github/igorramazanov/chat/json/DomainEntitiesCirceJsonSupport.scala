package com.github.igorramazanov.chat.json

import cats.data.Validated._
import cats.data.{NonEmptyChain, ValidatedNec}
import cats.implicits._
import com.github.igorramazanov.chat.domain._
import com.github.igorramazanov.chat.validation.DomainEntityValidationError.ValidationResult
import io.circe._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser.{decode, parse}
import io.circe.syntax._

object DomainEntitiesCirceJsonSupport extends DomainEntitiesJsonSupport {

  private def decodeWithoutValidation[A: Decoder](
      jsonString: String): Either[NonEmptyChain[String], A] =
    decode(jsonString).leftMap(error => NonEmptyChain(error.getMessage))

  //TODO replace by compile-time derivation/macro/reflection
  override implicit val userJsonApi: JsonApi[User] = new JsonApi[User] {

    private def collapseValidated[T](
        validationAndParsingResult: ValidatedNec[DecodingFailure,
                                                 ValidationResult[T]])
      : Either[NonEmptyChain[String], T] = {
      val withErrorsAsString = validationAndParsingResult.bimap(
        jsonDecodingFailures => jsonDecodingFailures.map(_.message),
        validationResult => validationResult.leftMap(_.map(_.errorMessage))
      )

      withErrorsAsString
        .fold(
          jsonDecodingFailures => jsonDecodingFailures.invalid[T],
          identity
        )
        .toEither
    }

    private def collapseEither[T](
        data: Either[String, Either[NonEmptyChain[String], T]]) =
      data.fold(
        s => NonEmptyChain(s).asLeft[T],
        identity
      )

    override def write(entity: User): String =
      Json
        .obj(
          "id" -> Json.fromString(entity.id.value),
          "password" -> Json.fromString(entity.password.value),
          "email" -> Json.fromString(entity.email.value)
        )
        .noSpaces

    override def read(
        jsonString: String): Either[NonEmptyChain[String], User] = {
      {
        val either = parse(jsonString).bimap(
          _.message,
          json => {
            val cursor = json.hcursor
            type DecodingResult[A] = ValidatedNec[DecodingFailure, A]

            val id: DecodingResult[String] = cursor
              .get[String]("id")
              .toValidatedNec
            val password: DecodingResult[String] = cursor
              .get[String]("password")
              .toValidatedNec
            val email: DecodingResult[String] = cursor
              .get[String]("email")
              .toValidatedNec

            val validated =
              (id, password, email).mapN(User.safeCreate)

            collapseValidated(validated)
          }
        )
        collapseEither(either)
      }
    }
  }
  override implicit val incomingChatMessageJsonApi
    : JsonApi[ChatMessage.IncomingChatMessage] =
    new JsonApi[ChatMessage.IncomingChatMessage] {
      implicit val decoder: Decoder[ChatMessage.IncomingChatMessage] =
        deriveDecoder
      implicit val encoder: Encoder[ChatMessage.IncomingChatMessage] =
        deriveEncoder

      override def write(entity: ChatMessage.IncomingChatMessage): String =
        entity.asJson.noSpaces

      override def read(jsonString: String)
        : Either[NonEmptyChain[String], ChatMessage.IncomingChatMessage] =
        decodeWithoutValidation(jsonString)
    }
  override implicit val generalChatMessageJsonApi
    : JsonApi[ChatMessage.GeneralChatMessage] =
    new JsonApi[ChatMessage.GeneralChatMessage] {
      implicit val decoder: Decoder[ChatMessage.GeneralChatMessage] =
        deriveDecoder
      implicit val encoder: Encoder[ChatMessage.GeneralChatMessage] =
        deriveEncoder

      override def write(entity: ChatMessage.GeneralChatMessage): String =
        entity.asJson.noSpaces
      override def read(jsonString: String)
        : Either[NonEmptyChain[String], ChatMessage.GeneralChatMessage] =
        decodeWithoutValidation(jsonString)
    }

  override implicit val signUpRequestJsonApi: JsonApi[SignUpRequest] =
    new JsonApi[SignUpRequest] {
      implicit val decoder: Decoder[SignUpRequest] =
        deriveDecoder
      implicit val encoder: Encoder[SignUpRequest] =
        deriveEncoder

      override def write(entity: SignUpRequest): String = entity.asJson.noSpaces
      override def read(
          jsonString: String): Either[NonEmptyChain[String], SignUpRequest] =
        decodeWithoutValidation(jsonString)
    }

  override implicit val invalidSignUpRequestJsonApi
    : JsonApi[InvalidSignUpRequest] = new JsonApi[InvalidSignUpRequest] {
    implicit val decoder: Decoder[InvalidSignUpRequest] =
      (c: HCursor) =>
        c.get[List[String]]("validationErrors").flatMap { errors =>
          NonEmptyChain
            .fromSeq(errors)
            .map(InvalidSignUpRequest)
            .map(invalidSignUpRequest =>
              invalidSignUpRequest.asRight[DecodingFailure])
            .getOrElse(DecodingFailure("validationErrors should not be empty",
                                       Nil).asLeft[InvalidSignUpRequest])

      }
    implicit val encoder: Encoder[InvalidSignUpRequest] =
      (a: InvalidSignUpRequest) =>
        Json.obj("validationErrors" -> a.validationErrors.toList.asJson)

    override def write(entity: InvalidSignUpRequest): String =
      entity.asJson.noSpaces
    override def read(jsonString: String)
      : Either[NonEmptyChain[String], InvalidSignUpRequest] =
      decodeWithoutValidation(jsonString)
  }

  override implicit val validSignUpRequestJsonApi: JsonApi[ValidSignUpRequest] =
    new JsonApi[ValidSignUpRequest] {
      override def write(entity: ValidSignUpRequest): String =
        userJsonApi.write(entity.asUser)
      override def read(jsonString: String)
        : Either[NonEmptyChain[String], ValidSignUpRequest] =
        userJsonApi.read(jsonString).map(ValidSignUpRequest.fromUser)
    }
}
