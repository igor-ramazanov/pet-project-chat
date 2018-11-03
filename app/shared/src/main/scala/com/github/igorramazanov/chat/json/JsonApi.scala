package com.github.igorramazanov.chat.json
import cats.data.NonEmptyChain
import com.github.igorramazanov.chat.domain.DomainEntity

trait JsonApi[Entity <: DomainEntity] {
  def write(entity: Entity): String

  def read(jsonString: String): Either[NonEmptyChain[String], Entity]
}

object JsonApi {
  def apply[Entity <: DomainEntity: JsonApi]: JsonApi[Entity] =
    implicitly[JsonApi[Entity]]
}
