package com.github.igorramazanov.chat.route
import akka.http.scaladsl.model.{HttpCharsets, HttpEntity, MediaTypes}
import akka.http.scaladsl.server.Route
import com.github.igorramazanov.chat.BuildInfo

object StaticFiles {
  def createRoute: Route = {
    import akka.http.scaladsl.server.Directives._
    get {
      getFromResourceDirectory("") ~
        pathSingleSlash {
          complete(
            HttpEntity(
              MediaTypes.`text/html`.withCharset(HttpCharsets.`UTF-8`),
              page
            )
          )
        }
    }
  }

  private val page = {
    import scalatags.Text.all._
    val (jsDependencies, frontend) =
      if (BuildInfo.mode.equalsIgnoreCase("prod"))
        (
          script(
            src := "/pet-project-chat-frontend-jsdeps.min.js",
            lang := "JavaScript"
          ),
          script(
            src := "/pet-project-chat-frontend-opt.js",
            lang := "JavaScript"
          )
        )
      else
        (
          script(
            src := "/pet-project-chat-frontend-jsdeps.js",
            lang := "JavaScript"
          ),
          script(
            src := "/pet-project-chat-frontend-fastopt.js",
            lang := "JavaScript"
          )
        )

    html(
      head(
        link(
          rel := "stylesheet",
          href := "https://stackpath.bootstrapcdn.com/bootstrap/4.1.3/css/bootstrap.min.css",
          attr(
            "integrity"
          ) := "sha384-MCw98/SFnGE8fJT3GXwEOngsV7Zt27NXFoaoApmYm81iuXoPkFOJwJ8ERdknLPMO",
          attr("crossorigin") := "anonymous"
        ),
        tag("style")(
          """
            | ::-webkit-scrollbar {
            |  display: none
            | }
            | html {
            |  overflow: -moz-scrollbars-none;
            | }
          """.stripMargin
        )
      ),
      body(
        div(id := "react-app"),
        script(
          src := "https://code.jquery.com/jquery-3.3.1.slim.min.js",
          attr(
            "integrity"
          ) := "sha384-q8i/X+965DzO0rT7abK41JStQIAqVgRVzpbzo5smXKp4YfRvH+8abtTE1Pi6jizo",
          attr("crossorigin") := "anonymous"
        ),
        script(
          src := "https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.14.3/umd/popper.min.js",
          attr(
            "integrity"
          ) := "sha384-ZMP7rVo3mIykV+2+9J3UJ46jBk0WLaUAdn689aCwoqbBJiSnjAK/l8WvCWPIPm49",
          attr("crossorigin") := "anonymous"
        ),
        script(
          src := "https://stackpath.bootstrapcdn.com/bootstrap/4.1.3/js/bootstrap.min.js",
          attr(
            "integrity"
          ) := "sha384-ChfqqxuZUCnJSK3+MXmPNIyE6ZbWh2IMqE241rYiqJxyMiZ6OW/JmZQ5stwEULTy",
          attr("crossorigin") := "anonymous"
        ),
        jsDependencies,
        frontend
      )
    ).render.getBytes(HttpCharsets.`UTF-8`.value)
  }
}
