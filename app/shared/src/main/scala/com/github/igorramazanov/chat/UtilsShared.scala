package com.github.igorramazanov.chat

object UtilsShared {
  implicit class AnyOps(val `this`: Any) extends AnyVal {
    @specialized def discard(): Unit = ()
  }
}
