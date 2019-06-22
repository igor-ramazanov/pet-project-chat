package com.github.igorramazanov.chat

object UtilsShared {
  implicit class AnyOps(val `this`: Any) extends AnyVal {
    @specialized def discard(): Unit = ()
  }

  val lowercase: Set[Char] = ('a' to 'z').toSet
  val uppercase: Set[Char] = ('A' to 'Z').toSet
  val digits: Set[Char]    = ('0' to '9').toSet
  val special: Set[Char] = (Char.MinValue to Char.MaxValue).toSet
    .diff(lowercase)
    .diff(uppercase)
    .diff(digits)
}
