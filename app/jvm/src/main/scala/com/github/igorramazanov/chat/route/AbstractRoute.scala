package com.github.igorramazanov.chat.route
import akka.http.scaladsl.marshalling.{
  Marshaller,
  PredefinedToResponseMarshallers
}
import akka.http.scaladsl.model.{HttpResponse, StatusCode}
import com.github.igorramazanov.chat.ResponseCode

trait AbstractRoute {
  implicit val httpStatusCodeMarshaller
      : Marshaller[ResponseCode, HttpResponse] =
    PredefinedToResponseMarshallers.fromStatusCode.compose(
      (code: ResponseCode) => StatusCode.int2StatusCode(code.value)
    )
}
