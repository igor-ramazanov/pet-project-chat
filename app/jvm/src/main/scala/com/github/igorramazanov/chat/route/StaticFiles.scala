package com.github.igorramazanov.chat.route
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._

object StaticFiles {
  def createRoute: Route = get {
    getFromResourceDirectory("") ~
      pathSingleSlash {
        getFromResource("index.html")
      }
  }
}
