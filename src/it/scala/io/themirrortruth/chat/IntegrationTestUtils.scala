package io.themirrortruth.chat

object IntegrationTestUtils {
  implicit class AnyOps(val `this`: Any) extends AnyVal {
    @specialized def discard(): Unit = ()
  }
}
