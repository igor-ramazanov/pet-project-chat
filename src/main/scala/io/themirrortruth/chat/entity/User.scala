package io.themirrortruth.chat.entity

import spray.json._

final case class User(id: String, password: String)

object User extends DefaultJsonProtocol {
  implicit val userJsonFormat: RootJsonFormat[User] = jsonFormat2(User.apply)
}
