package com.github.igorramazanov.chat.components
import com.github.igorramazanov.chat.domain.User
import japgolly.scalajs.react.extra.Ajax
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, ScalaComponent}
import com.github.igorramazanov.chat.json._

@SuppressWarnings(Array("org.wartremover.warts.Any"))
object MainComponent {
//  sealed trait Page extends Product with Serializable
//  object Page {
//    final case object WelcomingPage extends Page
//    final case object ChatPage extends Page
//  }

  final case class State()

  object State {
    def init: State = State()
  }

  final class Backend($ : BackendScope[Unit, State],
                      jsonSupport: DomainEntitiesJsonSupport) {
    import jsonSupport._
    import DomainEntitiesJsonSupport._
    def signIn(username: String, password: String): Callback = Callback {
      println(s"Sign in: $username $password")
    }

    def signUp(username: String, password: String): Callback = Callback {
      println("Signing up...")
      Ajax("POST", "http://localhost:8080/signup").setRequestContentTypeJsonUtf8
        .send(
          User(username, password).toJson
        )
        .onComplete { xhr =>
          Callback {
            xhr.status match {
              case 200 => println(s"Sign up: success")
              case _ =>
                println(
                  s"Sign up: failure, reason: ${Ajax.deriveErrorMessage(xhr)}")
            }
          }
        }
    }

    def render(s: State): VdomElement = {
      WelcomeComponent.Component(WelcomeComponent.Props(signIn, signUp))
    }
  }

  def Component(jsonSupport: DomainEntitiesJsonSupport) =
    ScalaComponent
      .builder[Unit]("MainComponent")
      .initialState(State.init)
      .backend(p => new Backend(p, jsonSupport))
      .renderBackend
      .build
}
