package io.github.igorramazanov.chat.json
import simulacrum.typeclass

@typeclass trait JsonApi[Entity] {
  def write(entity: Entity): String

  def read(jsonString: String): Either[String, Entity]
}
