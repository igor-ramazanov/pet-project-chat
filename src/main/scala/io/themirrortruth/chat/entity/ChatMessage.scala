package io.themirrortruth.chat.entity

import akka.http.scaladsl.model.DateTime
import spray.json.{DefaultJsonProtocol, _}

sealed trait ChatMessage extends Product with Serializable

object ChatMessage extends DefaultJsonProtocol {
  final case class IncomingChatMessage(to: String, payload: String)
      extends ChatMessage {
    def asGeneral(user: User) =
      GeneralChatMessage(user.id, to, payload, DateTime.now)
  }
  final case class OutgoingChatMessage(from: String,
                                       payload: String,
                                       datetime: DateTime)
  final case class GeneralChatMessage(from: String,
                                      to: String,
                                      payload: String,
                                      dateTime: DateTime) {
    def asOutgoing = OutgoingChatMessage(from, payload, dateTime)
  }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  implicit val dateTimeFormat: RootJsonFormat[DateTime] =
    new RootJsonFormat[DateTime] {
      override def write(obj: DateTime): JsValue =
        JsString(obj.toIsoDateTimeString())
      override def read(json: JsValue): DateTime = json match {
        case JsString(str) => DateTime.fromIsoDateTimeString(str).get
        case _             => DateTime.MinValue
      }
    }

  implicit val incomingChatMessageFormat: RootJsonFormat[IncomingChatMessage] =
    jsonFormat2(IncomingChatMessage)
  implicit val outgoingChatMessageFormat: RootJsonFormat[OutgoingChatMessage] =
    jsonFormat3(OutgoingChatMessage)
  implicit val generalChatMessageFormat: RootJsonFormat[GeneralChatMessage] =
    jsonFormat4(GeneralChatMessage)
}
