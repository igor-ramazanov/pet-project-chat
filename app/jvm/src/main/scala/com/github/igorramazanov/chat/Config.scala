package com.github.igorramazanov.chat
import java.text.ParseException

import scopt.{OptionParser, Read}

import scala.concurrent.duration.{FiniteDuration, _}

final case class Config(redisHost: String,
                        emailVerificationLinkPrefix: String,
                        logLevel: String,
                        emailVerificationTimeout: FiniteDuration) {

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  override def toString: String = {
    val fields = classOf[Config].getDeclaredFields
      .filterNot(_.isSynthetic)
      .map(_.getName)
    val values = this.productIterator.toSeq
    fields
      .zip(values)
      .foldLeft("") {
        case (acc, (name, value)) => acc + s"  $name = ${value.toString}\n"
      }
  }
}

object Config {
  val empty = Config("", "", "", Duration.Zero)

  private implicit val readFiniteDuration: Read[FiniteDuration] =
    Read.durationRead.map { d =>
      if (d.isFinite()) {
        FiniteDuration(d.toUnit(d.unit).toLong, d.unit)
      } else {
        throw new ParseException("Duration should be finite", -1)
      }
    }

  val parser: OptionParser[Config] =
    new OptionParser[Config]("pet-project-chat") {
      help("help") text "prints this help"

      opt[String]('h', "redis-host") required () action { (x, c) =>
        c.copy(redisHost = x)
      } text "(required) host of redis used as a storage"
      opt[String]('p', "verification-email-link-prefix") required () action {
        (x, c) =>
          c.copy(emailVerificationLinkPrefix = x)
      } text "(required) prefix of the verification link sent to clients on registration, specify address by which the server is accessible, i.e. 'http://localhost:8080'"
      opt[String]('l', "log-level") withFallback (() => "INFO") action {
        (x, c) =>
          c.copy(logLevel = x)
      } text "(optional) log level, must be one of 'OFF','ERROR','WARN','INFO','DEBUG','TRACE','ALL', default is INFO"
      opt[FiniteDuration]('t', "email-verification-timeout") withFallback (
          () => 1.day) action { (x, c) =>
        c.copy(emailVerificationTimeout = x)
      } text "(optional) email verification timeout, examples are '1 second', '9 days', '3 hours', '1 hour', default is '1 day'"
    }
}
