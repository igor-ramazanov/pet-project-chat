package com.github.igorramazanov.chat.api
import scala.concurrent.duration.FiniteDuration

trait KvStoreApi[Key, Value, F[_]] {
  def get(key: Key): F[Option[Value]]

  def del(key: Key): F[Unit]

  def set(key: Key, value: Value): F[Unit]

  def setWithExpiration(key: Key, value: Value, duration: FiniteDuration): F[Unit]

  def setIfEmpty(key: Key, value: Value): F[Boolean]
}
