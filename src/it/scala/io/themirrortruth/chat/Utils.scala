package io.themirrortruth.chat

object Utils {
  implicit class AnyOps(val `this`: Any) extends AnyVal {
    @specialized def discard(): Unit = ()
  }
}
