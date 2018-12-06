package com.github.igorramazanov.chat.config

import java.text.ParseException

import cats.syntax.option._
import com.github.igorramazanov.chat.config.Config._
import com.github.igorramazanov.chat.domain.User
import scopt.{OptionParser, Read}

import scala.concurrent.duration.{FiniteDuration, _}

final case class Config(
    redisHost: String,
    logLevel: String,
    private val smtpHost: Option[String],
    private val smtpPort: Option[Int],
    private val requireTls: Boolean,
    private val from: Option[User.Email],
    private val user: Option[String],
    private val password: Option[String],
    private val prefix: Option[String],
    private val timeout: Option[FiniteDuration]
) {

  def emailVerificationConfig: Option[Config.EmailVerificationConfig] =
    for {
      h <- smtpHost
      port <- smtpPort
      f <- from
      pref <- prefix
      t <- timeout
    } yield
      Config.EmailVerificationConfig(h,
                                     port,
                                     requireTls,
                                     f,
                                     user,
                                     password,
                                     pref,
                                     t)

  override def toString: String = configToString[this.type](getClass, this)
}

object Config {
  case class EmailVerificationConfig(smtpHost: String,
                                     smtpPort: Int,
                                     requireTls: Boolean,
                                     from: User.Email,
                                     user: Option[String],
                                     password: Option[String],
                                     prefix: String,
                                     timeout: FiniteDuration) {
    override def toString: String =
      configToString[this.type](getClass, this)
  }

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  private[config] def configToString[A <: Product](clazz: Class[_],
                                                   config: A) = {
    val fields = clazz.getDeclaredFields
      .filterNot(_.isSynthetic)
      .map(_.getName)
    val values = config.productIterator.toSeq
    fields
      .zip(values)
      .foldLeft("") {
        case (acc, (name, value)) if !name.contains("password") =>
          acc + s"  ${name.split('$').last} = ${value.toString}\n"
        case (acc, (name, value)) =>
          acc + s"  ${name.split('$').last} = ${value.toString.map(_ => 'X')}\n"
      }
  }

  val empty = Config("", "", None, None, false, None, None, None, None, None)

  private implicit val readFiniteDuration: Read[FiniteDuration] =
    Read.durationRead.map { d =>
      if (d.isFinite()) {
        FiniteDuration(d.toUnit(d.unit).toLong, d.unit)
      } else {
        throw new ParseException("Duration should be finite", -1)
      }
    }

  private implicit val readEmail: Read[User.Email] =
    Read.stringRead.map(
      s =>
        User.Email
          .validate(s)
          .fold(
            validationErrors =>
              throw new ParseException(
                s"Couldn't validate email from string: $s. Reasons: ${validationErrors.toString}",
                -1),
            identity
        ))

  val parser: OptionParser[Config] =
    new OptionParser[Config]("pet-project-chat") {
      help("help") text "prints this help"
      opt[String]("redis-host") required () action { (x, c) =>
        c.copy(redisHost = x)
      } text "(required) host of redis used as a storage"

      opt[String]("log-level") withFallback (() => "INFO") action { (x, c) =>
        c.copy(logLevel = x)
      } text "(optional) log level, must be one of 'OFF','ERROR','WARN','INFO','DEBUG','TRACE','ALL', default is INFO"

      opt[String]("smtp-host") action { (x, c) =>
        c.copy(smtpHost = x.some)
      } text "(optional) SMTP host, if not provided then verification of emails be disabled"

      opt[Int]("smtp-port") action { (x, c) =>
        c.copy(smtpPort = x.some)
      } text "(optional) SMTP port, if not provided then verification of emails be disabled"

      opt[Unit]("smtp-tls") action { (_, c) =>
        c.copy(requireTls = true)
      } text "(optional) Use TLS for SMTP"

      opt[User.Email]("smtp-from") action { (x, c) =>
        c.copy(from = x.some)
      } text "(optional) 'from' field of verification emails, if not provided then verification of emails be disabled"

      opt[String]("smtp-user") action { (x, c) =>
        c.copy(user = x.some)
      } text "(optional) User for SMTP server"

      opt[String]("smtp-password") action { (x, c) =>
        c.copy(password = x.some)
      } text "(optional) Password for SMTP server"

      opt[String]("verification-link-prefix") action { (x, c) =>
        c.copy(prefix = x.some)
      } text "(optional) Prefix of email verification link, i.e. 'http://localhost:8080', if not provided then verification of emails be disabled"

      opt[FiniteDuration]("email-verification-timeout") withFallback (
          () => 1.day) action { (x, c) =>
        c.copy(timeout = x.some)
      } text "(optional) email verification timeout, examples are '1 second', '9 days', '3 hours', '1 hour', default is '1 day'"

    }
}
