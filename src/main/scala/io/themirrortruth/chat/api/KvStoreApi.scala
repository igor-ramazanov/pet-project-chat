package io.themirrortruth.chat.api

trait KvStoreApi[Key, Value, F[_]] {
  def get(key: Key): F[Option[Value]]

  def set(key: Key, value: Value): F[Unit]

  def setIfEmpty(key: Key, value: Value): F[Boolean]
}
