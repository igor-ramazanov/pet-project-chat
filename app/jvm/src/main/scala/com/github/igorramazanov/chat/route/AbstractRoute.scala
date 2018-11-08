package com.github.igorramazanov.chat.route
import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers
import akka.http.scaladsl.model.StatusCode
import com.github.igorramazanov.chat.HttpStatusCode

trait AbstractRoute {
  implicit val httpStatusCodeMarshaller =
    PredefinedToResponseMarshallers.fromStatusCode.compose(
      (code: HttpStatusCode) => StatusCode.int2StatusCode(code.value))
}
