package io.github.igorramazanov.chat
import io.github.igorramazanov.chat.api.{
  IncomingMessagesApi,
  KvStoreApi,
  OutgoingMessagesApi,
  PersistenceMessagesApi
}
import simulacrum.typeclass

@typeclass trait InterpretersInstances[F[_]] {
  implicit def kvStoreApi: KvStoreApi[String, String, F]
  implicit def outgoingApi: OutgoingMessagesApi[F]
  implicit def incomingApi: IncomingMessagesApi
  implicit def persistenceApi: PersistenceMessagesApi[F]
}
