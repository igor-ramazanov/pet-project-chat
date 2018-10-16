package io.github.igorramazanov.chat.domain

import akka.http.scaladsl.model.DateTime
import spray.json.{DefaultJsonProtocol, _}

sealed trait ChatMessage extends Product with Serializable

object ChatMessage {
  final case class IncomingChatMessage(to: String, payload: String)
      extends ChatMessage {
    def asGeneral(from: User) =
      GeneralChatMessage(from = from.id,
                         to = to,
                         payload = payload,
                         dateTime = DateTime.now)
  }
  final case class GeneralChatMessage(from: String,
                                      to: String,
                                      payload: String,
                                      dateTime: DateTime)
}

object ChatMessageJsonSupport extends DefaultJsonProtocol {
  import io.github.igorramazanov.chat.domain.ChatMessage.{
    GeneralChatMessage,
    IncomingChatMessage
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
  implicit val generalChatMessageFormat: RootJsonFormat[GeneralChatMessage] =
    jsonFormat4(GeneralChatMessage)
}
