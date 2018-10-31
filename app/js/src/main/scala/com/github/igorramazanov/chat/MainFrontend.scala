package com.github.igorramazanov.chat
import com.github.igorramazanov.chat.components._
import com.github.igorramazanov.chat.json.DomainEntitiesRegexJsonSupport
import org.scalajs.dom.document
import scalacss.DevDefaults._
import scalacss.ScalaCssReact._
@SuppressWarnings(Array("org.wartremover.warts.Any"))
object MainFrontend {
  def main(args: Array[String]): Unit = {

    MessageSendingComponent.Styles.addToDocument()
    ContactsComponent.Styles.addToDocument()
    WelcomeComponent.Styles.addToDocument()
    MessagesComponent.Styles.addToDocument()

    MainComponent
      .Component(DomainEntitiesRegexJsonSupport)()
      .renderIntoDOM(document.getElementById("react-app"))
  }
}
