package com.github.igorramazanov.chat

import com.github.igorramazanov.chat.Component._
import org.scalajs.dom.document

@SuppressWarnings(Array("org.wartremover.warts.Any"))
object MainFrontend {
  def main(args: Array[String]): Unit = {
    Component
      .Component(Props())
      .renderIntoDOM(document.getElementById("react-app"))
  }
}
