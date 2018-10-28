package com.github.igorramazanov.chat
import com.github.igorramazanov.chat.components.MainComponent
import com.github.igorramazanov.chat.domain.{ChatMessage, User}
import com.github.igorramazanov.chat.json.{DomainEntitiesJsonSupport, JsonApi}
import org.scalajs.dom.document
@SuppressWarnings(Array("org.wartremover.warts.Any"))
object MainFrontend {
  def main(args: Array[String]): Unit = {
    val jsonSupport: DomainEntitiesJsonSupport = new DomainEntitiesJsonSupport {
      override implicit def userJsonApi: JsonApi[User] = new JsonApi[User] {
        override def write(entity: User): String =
          s"""{"id":"${entity.id}","password":"${entity.password}"}"""

        override def read(jsonString: String): Either[String, User] = ???
      }
      override implicit def incomingChatMessageJsonApi
        : JsonApi[ChatMessage.IncomingChatMessage] = ???
      override implicit def generalChatMessageJsonApi
        : JsonApi[ChatMessage.GeneralChatMessage] = ???
    }

    MainComponent
      .Component(jsonSupport)()
      .renderIntoDOM(document.getElementById("react-app"))
  }
}
