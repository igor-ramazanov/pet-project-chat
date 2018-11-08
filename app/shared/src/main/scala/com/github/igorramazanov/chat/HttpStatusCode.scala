package com.github.igorramazanov.chat

sealed trait HttpStatusCode extends Product with Serializable {
  def value: Int
}

object HttpStatusCode {
  final case object UserAlreadyExists extends HttpStatusCode {
    override val value: Int = 409
  }
  final case object UserDoesNotExists extends HttpStatusCode {
    override val value: Int = 404
  }
  final case object InvalidCredentials extends HttpStatusCode {
    override val value: Int = 403
  }
  final case object SuccessfullySentVerificationEmail extends HttpStatusCode {
    override val value: Int = 202
  }
  final case object Ok extends HttpStatusCode {
    override val value: Int = 200
  }
  final case object EmailWasNotVerifiedInTime extends HttpStatusCode {
    override val value: Int = 410
  }
  final case object ValidationErrors extends HttpStatusCode {
    override val value: Int = 400
  }
  final case object ServerError extends HttpStatusCode {
    override val value: Int = 500
  }
}
