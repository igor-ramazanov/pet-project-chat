package com.github.igorramazanov.chat.interpreter.gmail
import java.io.{File, InputStreamReader}
import java.util.Properties

import cats.effect.{Async, Timer}
import cats.syntax.either._
import cats.syntax.functor._
import com.github.igorramazanov.chat.Utils._
import com.github.igorramazanov.chat.UtilsShared._
import com.github.igorramazanov.chat.api.{
  EmailApi,
  EmailWasNotVerifiedInTime,
  KvStoreApi
}
import com.github.igorramazanov.chat.domain.User.Email
import com.github.igorramazanov.chat.domain.{SignUpRequest, User}
import com.github.igorramazanov.chat.json.DomainEntitiesJsonSupport
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, _}

object EmailApiToKvStoreGmailInterpreter {
  private val logger = LoggerFactory.getLogger(this.getClass)

  private object GmailApi {
    //Copy paste from
    //https://developers.google.com/gmail/api/quickstart/java
    //https://developers.google.com/gmail/api/guides/sending

    import java.io.{ByteArrayOutputStream, IOException}
    import java.util.Collections

    import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
    import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
    import com.google.api.client.googleapis.auth.oauth2.{
      GoogleAuthorizationCodeFlow,
      GoogleClientSecrets
    }
    import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
    import com.google.api.client.http.javanet.NetHttpTransport
    import com.google.api.client.json.jackson2.JacksonFactory
    import com.google.api.client.util.Base64
    import com.google.api.client.util.store.FileDataStoreFactory
    import com.google.api.services.gmail.model.Message
    import com.google.api.services.gmail.{Gmail, GmailScopes}
    import javax.mail.internet.{InternetAddress, MimeMessage}
    import javax.mail.{MessagingException, Session}

    private val APPLICATION_NAME =
      "Gmail API Igor Ramazanov pet-project-chat email verification"
    private val userId = "me"
    private val TOKENS_DIRECTORY_PATH = "tokens"
    private val SCOPES =
      Collections.singletonList(GmailScopes.GMAIL_SEND)
    private val CREDENTIALS_FILE_PATH = "/credentials.json"
    private val JSON_FACTORY = JacksonFactory.getDefaultInstance
    private val HTTP_TRANSPORT =
      GoogleNetHttpTransport.newTrustedTransport
    private val service = new Gmail.Builder(HTTP_TRANSPORT,
                                            JSON_FACTORY,
                                            getCredentials(HTTP_TRANSPORT))
      .setApplicationName(APPLICATION_NAME)
      .build
    private val from = "themirrortruth@gmail.com"
    private val subject = "Email verification"

    @throws[Throwable]
    private def getCredentials(HTTP_TRANSPORT: NetHttpTransport) = {
      val in = getClass.getResourceAsStream(CREDENTIALS_FILE_PATH)
      val clientSecrets =
        GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in))
      val flow =
        new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT,
                                                JSON_FACTORY,
                                                clientSecrets,
                                                SCOPES)
          .setDataStoreFactory(
            new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
          .setAccessType("offline")
          .build
      val receiver =
        new LocalServerReceiver.Builder().setPort(8888).build
      new AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    }

    def send(verificationLinkPrefix: String)(
        to: Email,
        emailVerificationId: Email.VerificationId)(
        implicit executionContext: ExecutionContext): Future[Unit] =
      Future {
        blocking {
          service
            .users()
            .messages()
            .send(userId,
                  createMessage(verificationLinkPrefix)(to,
                                                        emailVerificationId))
            .execute()
            .discard()
        }
      }
    @throws[MessagingException]
    @throws[IOException]
    private def createMessage(verificationLinkPrefix: String)(
        to: Email,
        emailVerificationId: Email.VerificationId): Message = {
      val session = Session.getDefaultInstance(new Properties(), null)
      val email = new MimeMessage(session)
      email.setFrom(new InternetAddress(from))
      email.addRecipient(javax.mail.Message.RecipientType.TO,
                         new InternetAddress(to.value))
      email.setSubject(subject)
      email.setText(
        s"Verify registration by clicking on $verificationLinkPrefix/verify/${emailVerificationId.value}")

      val buffer = new ByteArrayOutputStream
      email.writeTo(buffer)
      val encodedEmail = Base64.encodeBase64URLSafeString(buffer.toByteArray)
      val message = new Message
      message.setRaw(encodedEmail)
      message
    }
  }

  implicit def emailApiToKvStoreGmail[F[_]: Async: Timer](
      implicit kvStoreApi: KvStoreApi[String, String, F],
      jsonSupport: DomainEntitiesJsonSupport,
      executionContext: ExecutionContext): EmailApi[F] = new EmailApi[F] {
    import DomainEntitiesJsonSupport._
    import jsonSupport._

    override def saveRequestWithExpiration(
        signUpRequest: SignUpRequest,
        duration: FiniteDuration): F[Email.VerificationId] = {
      val rawId = java.util.UUID.randomUUID().toString
      val id = Email.VerificationId(rawId)
      kvStoreApi
        .setWithExpiration(rawId, signUpRequest.toJson, duration)
        .map(_ => id)
    }

    override def markAsVerified(emailVerificationId: Email.VerificationId)
      : F[Either[EmailWasNotVerifiedInTime.type, User]] = {

      kvStoreApi.get(emailVerificationId.value).map { maybeRawSignUpRequest =>
        val maybeSignUpRequest = maybeRawSignUpRequest.flatMap {
          rawSignUpRequest =>
            rawSignUpRequest.toSignUpRequest.toOption
        }

        maybeSignUpRequest
          .map(_.unsafeToUser.asRight[EmailWasNotVerifiedInTime.type])
          .getOrElse(EmailWasNotVerifiedInTime.asLeft[User])
      }
    }

    override def sendVerificationEmail(verificationLinkPrefix: String)(
        to: Email,
        emailVerificationId: Email.VerificationId): F[Either[Throwable, Unit]] =
      liftFromFuture(
        GmailApi
          .send(verificationLinkPrefix)(to, emailVerificationId)
          .map(_.asRight[Throwable])
          .recover { case e => e.asLeft[Unit] },
        logger.error(s"Couldn't send verification email to email '${to.value}'",
                     _)
      )
  }
}
