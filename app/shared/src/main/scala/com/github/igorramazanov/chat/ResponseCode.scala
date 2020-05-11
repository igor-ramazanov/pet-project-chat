package com.github.igorramazanov.chat

sealed trait ResponseCode extends Product with Serializable {
  def value: Int
}

object ResponseCode {
  final case object UserAlreadyExists                 extends ResponseCode {
    override val value: Int = 409
  }
  final case object UserDoesNotExists                 extends ResponseCode {
    override val value: Int = 404
  }
  final case object InvalidCredentials                extends ResponseCode {
    override val value: Int = 403
  }
  final case object SuccessfullySentVerificationEmail extends ResponseCode {
    override val value: Int = 202
  }
  final case object Ok                                extends ResponseCode {
    override val value: Int = 200
  }
  final case object EmailWasNotVerifiedInTime         extends ResponseCode {
    override val value: Int = 410
  }
  final case object ValidationErrors                  extends ResponseCode {
    override val value: Int = 400
  }
  final case object ServerError                       extends ResponseCode {
    override val value: Int = 500
  }
}
