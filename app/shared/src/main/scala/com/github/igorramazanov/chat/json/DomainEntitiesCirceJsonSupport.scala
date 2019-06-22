package com.github.igorramazanov.chat.json

import cats.data.Validated._
import cats.data.{NonEmptyChain, ValidatedNec}
import cats.implicits._
import com.github.igorramazanov.chat.domain._
import com.github.igorramazanov.chat.validation.DomainEntityValidationError
import io.circe._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser.parse
import io.circe.syntax._

object DomainEntitiesCirceJsonSupport extends DomainEntitiesJsonSupport {
  private implicit val idEncoder: Encoder[User.Id] = new Encoder[User.Id] {
    override def apply(a: User.Id): Json = Json.fromString(a.value)
  }
  private implicit val passwordEncoder: Encoder[User.Password] =
    new Encoder[User.Password] {
      override def apply(a: User.Password): Json = Json.fromString(a.value)
    }
  private implicit val emailEncoder: Encoder[User.Email] =
    new Encoder[User.Email] {
      override def apply(a: User.Email): Json = Json.fromString(a.value)
    }

  private type DecodingAndValidationResult[A] = ValidatedNec[String, A]

  private def decode[A](
      c: HCursor,
      field: String,
      validate: String => DomainEntityValidationError.ValidationResult[A]
  ): DecodingAndValidationResult[A] =
    c.get[String](field)
      .fold(
        _.message.invalidNec[A],
        validate andThen { _.leftMap(_.map(_.errorMessage)) }
      )

  private def decode[A: Decoder](c: HCursor, field: String): DecodingAndValidationResult[A] =
    c.get[A](field)
      .fold(
        _.message.invalidNec[A],
        _.validNec[String]
      )

  private def parsingFailureToLeft[A](parsingFailure: ParsingFailure) =
    NonEmptyChain.one(parsingFailure.message).asLeft[A]

  //TODO replace by compile-time derivation/macro/reflection
  override implicit val userJsonApi: JsonApi[User] = new JsonApi[User] {
    implicit val encoder: Encoder[User] = new Encoder[User] {
      override def apply(a: User): Json = Json.obj(
        "id"       -> idEncoder(a.id),
        "password" -> passwordEncoder(a.password),
        "email"    -> emailEncoder(a.email)
      )
    }

    override def write(entity: User): String =
      entity.asJson.noSpaces

    override def read(jsonString: String): Either[NonEmptyChain[String], User] =
      parse(jsonString)
        .fold(
          parsingFailureToLeft[User], { json =>
            val c        = json.hcursor
            val id       = decode(c, "id", User.Id.validate)
            val password = decode(c, "password", User.Password.validate)
            val email    = decode(c, "email", User.Email.validate)

            (id, password, email).mapN(User.safeCreate).toEither
          }
        )
  }

  //TODO replace by compile-time derivation/macro/reflection
  override implicit val incomingChatMessageJsonApi: JsonApi[ChatMessage.IncomingChatMessage] =
    new JsonApi[ChatMessage.IncomingChatMessage] {
      implicit val encoder: Encoder[ChatMessage.IncomingChatMessage] =
        new Encoder[ChatMessage.IncomingChatMessage] {
          override def apply(a: ChatMessage.IncomingChatMessage): Json =
            Json.obj(
              "to"      -> idEncoder(a.to),
              "payload" -> Json.fromString(a.payload)
            )
        }

      override def write(entity: ChatMessage.IncomingChatMessage): String =
        entity.asJson.noSpaces

      override def read(
          jsonString: String
      ): Either[NonEmptyChain[String], ChatMessage.IncomingChatMessage] =
        parse(jsonString).fold(
          parsingFailureToLeft[ChatMessage.IncomingChatMessage], { json =>
            val c       = json.hcursor
            val to      = decode(c, "to", User.Id.validate)
            val payload = decode[String](c, "payload")
            (to, payload).mapN(ChatMessage.IncomingChatMessage.apply).toEither
          }
        )
    }

  //TODO replace by compile-time derivation/macro/reflection
  override implicit val generalChatMessageJsonApi: JsonApi[ChatMessage.GeneralChatMessage] =
    new JsonApi[ChatMessage.GeneralChatMessage] {
      implicit val encoder: Encoder[ChatMessage.GeneralChatMessage] =
        new Encoder[ChatMessage.GeneralChatMessage] {
          override def apply(a: ChatMessage.GeneralChatMessage): Json =
            Json.obj(
              "to"                      -> idEncoder(a.to),
              "from"                    -> idEncoder(a.from),
              "payload"                 -> Json.fromString(a.payload),
              "dateTimeUtcEpochSeconds" -> Json.fromLong(a.dateTimeUtcEpochSeconds)
            )
        }

      override def write(entity: ChatMessage.GeneralChatMessage): String =
        entity.asJson.noSpaces

      override def read(
          jsonString: String
      ): Either[NonEmptyChain[String], ChatMessage.GeneralChatMessage] =
        parse(jsonString).fold(
          parsingFailureToLeft[ChatMessage.GeneralChatMessage], { json =>
            val c       = json.hcursor
            val from    = decode(c, "from", User.Id.validate)
            val to      = decode(c, "to", User.Id.validate)
            val payload = decode[String](c, "payload")
            val time    = decode[Long](c, "dateTimeUtcEpochSeconds")

            (from, to, payload, time)
              .mapN(ChatMessage.GeneralChatMessage.apply)
              .toEither
          }
        )
    }

  override implicit val signUpRequestJsonApi: JsonApi[SignUpOrInRequest] =
    new JsonApi[SignUpOrInRequest] {
      implicit val decoder: Decoder[SignUpOrInRequest] =
        deriveDecoder
      implicit val encoder: Encoder[SignUpOrInRequest] =
        deriveEncoder

      override def write(entity: SignUpOrInRequest): String =
        entity.asJson.noSpaces

      override def read(jsonString: String): Either[NonEmptyChain[String], SignUpOrInRequest] =
        io.circe.parser
          .decode[SignUpOrInRequest](jsonString)
          .leftMap(e => NonEmptyChain.one(e.getMessage))
    }

  override implicit val invalidSignUpRequestJsonApi: JsonApi[InvalidRequest] =
    new JsonApi[InvalidRequest] {
      implicit val decoder: Decoder[InvalidRequest] =
        (c: HCursor) =>
          c.get[List[String]]("validationErrors").flatMap { errors =>
            NonEmptyChain
              .fromSeq(errors)
              .map(InvalidRequest)
              .map(invalidRequest => invalidRequest.asRight[DecodingFailure])
              .getOrElse(
                DecodingFailure("validationErrors should not be empty", Nil).asLeft[InvalidRequest]
              )

          }
      implicit val encoder: Encoder[InvalidRequest] =
        (a: InvalidRequest) => Json.obj("validationErrors" -> a.validationErrors.toList.asJson)

      override def write(entity: InvalidRequest): String =
        entity.asJson.noSpaces

      override def read(jsonString: String): Either[NonEmptyChain[String], InvalidRequest] =
        io.circe.parser
          .decode[InvalidRequest](jsonString)
          .leftMap(e => NonEmptyChain.one(e.getMessage))

    }

  override implicit val validSignUpRequestJsonApi: JsonApi[ValidSignUpOrInRequest] =
    new JsonApi[ValidSignUpOrInRequest] {
      override def write(entity: ValidSignUpOrInRequest): String =
        userJsonApi.write(entity.asUser)
      override def read(jsonString: String): Either[NonEmptyChain[String], ValidSignUpOrInRequest] =
        userJsonApi.read(jsonString).map(ValidSignUpOrInRequest.fromUser)
    }
}
