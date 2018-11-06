package com.github.igorramazanov.chat

object HttpStatusCodes {
  val UserAlreadyExists = 409
  val SuccessfullySentVerificationEmail = 202
  val SignedUp = 200
  val EmailWasNotVerifiedInTime = 410
  val ValidationErrors = 400
}
