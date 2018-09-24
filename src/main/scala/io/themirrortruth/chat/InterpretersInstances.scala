package io.themirrortruth.chat
import io.themirrortruth.chat.api.{
  IncomingMessagesApi,
  KvStoreApi,
  OutgoingMesssagesApi,
  PersistenceMessagesApi
}
import simulacrum.typeclass

@typeclass trait InterpretersInstances[F[_]] {
  implicit val kvStoreApi: KvStoreApi[String, String, F]
  implicit val outgoingApi: OutgoingMesssagesApi[F]
  implicit val incomingApi: IncomingMessagesApi
  implicit val persistenceApi: PersistenceMessagesApi[F]
}
