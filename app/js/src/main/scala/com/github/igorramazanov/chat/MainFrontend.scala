package com.github.igorramazanov.chat
import com.github.igorramazanov.chat.components.MainComponent
import com.github.igorramazanov.chat.json.DomainEntitiesRegexJsonSupport
import org.scalajs.dom.document
@SuppressWarnings(Array("org.wartremover.warts.Any"))
object MainFrontend {
  def main(args: Array[String]): Unit = {
    MainComponent
      .Component(DomainEntitiesRegexJsonSupport)()
      .renderIntoDOM(document.getElementById("react-app"))
  }
}
