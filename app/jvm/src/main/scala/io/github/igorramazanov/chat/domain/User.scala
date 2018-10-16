package io.github.igorramazanov.chat.domain

import spray.json._

final case class User(id: String, password: String)

object UserJsonSupport extends DefaultJsonProtocol {
  implicit val userJsonFormat: RootJsonFormat[User] = jsonFormat2(User.apply)
}
