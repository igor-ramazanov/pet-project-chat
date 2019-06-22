package com.github.igorramazanov.chat
import com.github.igorramazanov.chat.UtilsShared._
import com.github.igorramazanov.chat.components._
import com.github.igorramazanov.chat.domain._
import com.github.igorramazanov.chat.json._
import com.github.igorramazanov.chat.validation._
import org.scalajs.dom.document
import scalacss.DevDefaults._
import scalacss.ScalaCssReact._

@SuppressWarnings(Array("org.wartremover.warts.Any"))
object MainFrontend {
  def main(args: Array[String]): Unit = {
    initializeObjects()
    MessageSendingComponent.Styles.addToDocument()
    ContactsComponent.Styles.addToDocument()
    WelcomeComponent.Styles.addToDocument()
    MessagesComponent.Styles.addToDocument()
    AlertsComponent.Styles.addToDocument()

    MainComponent
      .Component(DomainEntitiesCirceJsonSupport)()
      .renderIntoDOM(document.getElementById("react-app"))
  }

  private def initializeObjects(): Unit =
    List(
      UtilsShared,
      ResponseCode,
      DomainEntitiesJsonSupport,
      DomainEntitiesCirceJsonSupport,
      JsonApi,
      ChatMessage,
      KeepAliveMessage,
      KeepAliveMessage.Ping,
      KeepAliveMessage.Pong,
      ValidSignUpOrInRequest,
      User,
      User.Id,
      User.Password,
      User.Email,
      User.Implicits,
      DomainEntityValidationError,
      IdValidationError,
      IdValidationError.ContainsNotOnlyLowercaseLatinCharacters,
      IdValidationError.IsEmpty,
      PasswordValidationError,
      PasswordValidationError.LessThan10Characters,
      PasswordValidationError.LongerThan128Characters,
      PasswordValidationError.Contains2SameAdjacentCharacters,
      PasswordValidationError.DoesNotContainLowercaseCharacter,
      PasswordValidationError.DoesNotContainUppercaseCharacter,
      PasswordValidationError.DoesNotContainSpecialCharacter,
      PasswordValidationError.DoesNotContainDigitCharacter,
      EmailValidationError,
      EmailValidationError.`DoesNotContainAtLeast1@Character`
    ).discard()
}
