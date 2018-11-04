package com.github.igorramazanov.chat.route
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, path}
import akka.http.scaladsl.server.Route

object Status {
  def createRoute: Route = path("status") {
    complete(StatusCodes.OK)
  }
}
