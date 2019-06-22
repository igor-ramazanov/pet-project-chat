package com.github.igorramazanov.chat.interpreter
import java.util.Properties

import cats.effect.{Async, Timer}
import cats.syntax.either._
import cats.syntax.functor._
import com.github.igorramazanov.chat.Utils._
import com.github.igorramazanov.chat.api.{EmailApi, EmailWasNotVerifiedInTime, KvStoreApi}
import com.github.igorramazanov.chat.config.Config.EmailVerificationConfig
import com.github.igorramazanov.chat.domain.User.Email
import com.github.igorramazanov.chat.domain.{User, ValidSignUpOrInRequest}
import com.github.igorramazanov.chat.json.DomainEntitiesJsonSupport
import javax.mail.internet.{InternetAddress, MimeMessage}
import javax.mail._
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{Future, _}
class EmailApiToKvStoreApiInterpreter[F[_]: Async: Timer](c: EmailVerificationConfig)(
    implicit kvStoreApi: KvStoreApi[String, String, F],
    jsonSupport: DomainEntitiesJsonSupport,
    executionContext: ExecutionContext
) extends EmailApi[F] {
  private val logger: Logger = LoggerFactory.getLogger(getClass)
  import DomainEntitiesJsonSupport._
  import jsonSupport._

  private val session: Session = {
    val props = new Properties()
    props.setProperty("mail.smtp.host", c.smtpHost)
    props.setProperty("mail.smtp.port", c.smtpPort.toString)

    if (c.requireTls) {
      props.setProperty("mail.smtp.starttls.enable", "true")
    }

    {
      for {
        u <- c.user
        p <- c.password
      } yield {
        props.setProperty("mail.smtp.auth", "true")
        val auth = new Authenticator {
          override def getPasswordAuthentication: PasswordAuthentication =
            new PasswordAuthentication(u, p)
        }
        Session.getDefaultInstance(props, auth)
      }
    } getOrElse {
      Session.getDefaultInstance(props)
    }
  }

  override def saveRequestWithExpiration(
      signUpRequest: ValidSignUpOrInRequest
  ): F[Email.VerificationId] = {
    val rawId = java.util.UUID.randomUUID().toString
    val id    = Email.VerificationId(rawId)
    kvStoreApi
      .setWithExpiration(rawId, signUpRequest.toJson, c.timeout)
      .map { _ =>
        logger.debug(s"Saved email verifying request: ${signUpRequest.toString} for ${c.timeout
          .toString()}, returned id: ${id.value}")
        id
      }
  }
  override def checkRequestIsExpired(
      emailVerificationId: Email.VerificationId
  ): F[Either[EmailWasNotVerifiedInTime.type, ValidSignUpOrInRequest]] = {
    val key = emailVerificationId.value

    kvStoreApi.get(key).map { maybeRawSignUpRequest =>
      val maybeSignUpRequest = maybeRawSignUpRequest.flatMap { rawSignUpRequest =>
        rawSignUpRequest.toValidSignUpRequest.toOption
      }

      val either = maybeSignUpRequest
        .map(_.asRight[EmailWasNotVerifiedInTime.type])
        .getOrElse(EmailWasNotVerifiedInTime.asLeft[ValidSignUpOrInRequest])

      either.fold(
        _ => logger.debug(s"Email with id '${emailVerificationId.value}' was not verified in time"),
        user =>
          logger.debug(
            s"Email with id '${emailVerificationId.value}' of user ${user.toString} verified in time"
          )
      )
      either
    }
  }
  override def deleteRequest(emailVerificationId: Email.VerificationId): F[Unit] =
    kvStoreApi
      .del(emailVerificationId.value)
      .map(
        _ =>
          logger.debug(s"Deleted email verification request with id '${emailVerificationId.value}'")
      )
  override def sendVerificationEmail(
      to: User.Email,
      emailVerificationId: Email.VerificationId
  ): F[Either[Throwable, Unit]] =
    liftFromFuture(
      Future {
        blocking {
          try {
            val message = new MimeMessage(session)
            message.setFrom(new InternetAddress(c.from.value))
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to.value))
            message.setSubject("Igor Ramazanov's pet-project-chat email verification")
            message.setContent(
              s"""<p>Verify registration by clicking on <a href="${c.prefix}/verify/${emailVerificationId.value}">the link</a></p>""",
              "text/html"
            )
            Transport.send(message).asRight[Throwable]
          } catch {
            case e: Throwable => e.asLeft[Unit]
          }
        }
      },
      logger.error(s"Couldn't send verification email", _)
    )
}
