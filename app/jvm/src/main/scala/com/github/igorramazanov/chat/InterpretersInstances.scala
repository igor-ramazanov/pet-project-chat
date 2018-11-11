package com.github.igorramazanov.chat

import com.github.igorramazanov.chat.api._
import simulacrum.typeclass

@typeclass trait InterpretersInstances[F[_]] {
  implicit def kvStoreApi: KvStoreApi[String, String, F]
  implicit def outgoingApi: RealtimeOutgoingMessagesApi[F]
  implicit def incomingApi: RealtimeIncomingMessagesApi[F]
  implicit def persistenceApi: PersistenceMessagesApi[F]
}
