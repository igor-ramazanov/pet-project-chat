package com.github.igorramazanov.chat.domain

sealed trait KeepAliveMessage
object KeepAliveMessage {
  final object Ping extends KeepAliveMessage {
    override def toString: String = "ping"
  }
  final object Pong extends KeepAliveMessage {
    override def toString: String = "pong"
  }
}
