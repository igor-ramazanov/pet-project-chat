package com.github.igorramazanov.chat.route
import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers
import akka.http.scaladsl.model.StatusCode
import com.github.igorramazanov.chat.ResponseCode

trait AbstractRoute {
  implicit val httpStatusCodeMarshaller =
    PredefinedToResponseMarshallers.fromStatusCode.compose(
      (code: ResponseCode) => StatusCode.int2StatusCode(code.value)
    )
}
