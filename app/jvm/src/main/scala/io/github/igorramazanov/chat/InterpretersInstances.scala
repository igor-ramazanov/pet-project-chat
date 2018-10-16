package io.github.igorramazanov.chat
import io.github.igorramazanov.chat.api.{
  IncomingMessagesApi,
  KvStoreApi,
  OutgoingMesssagesApi,
  PersistenceMessagesApi
}
import simulacrum.typeclass

@typeclass trait InterpretersInstances[F[_]] {
  implicit def kvStoreApi: KvStoreApi[String, String, F]
  implicit def outgoingApi: OutgoingMesssagesApi[F]
  implicit def incomingApi: IncomingMessagesApi
  implicit def persistenceApi: PersistenceMessagesApi[F]
}
