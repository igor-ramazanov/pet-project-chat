package com.github.igorramazanov.chat

import com.github.igorramazanov.chat.api.PersistenceMessagesApi
import com.github.igorramazanov.chat.api.{
  IncomingMessagesApi,
  KvStoreApi,
  OutgoingMessagesApi
}
import simulacrum.typeclass

@typeclass trait InterpretersInstances[F[_]] {
  implicit def kvStoreApi: KvStoreApi[String, String, F]
  implicit def outgoingApi: OutgoingMessagesApi[F]
  implicit def incomingApi: IncomingMessagesApi
  implicit def persistenceApi: PersistenceMessagesApi[F]
}
