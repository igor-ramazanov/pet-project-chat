package com.github.igorramazanov.chat.json

trait JsonApi[Entity] {
  def write(entity: Entity): String

  def read(jsonString: String): Either[String, Entity]
}

object JsonApi {
  def apply[Entity: JsonApi]: JsonApi[Entity] = implicitly[JsonApi[Entity]]
}
