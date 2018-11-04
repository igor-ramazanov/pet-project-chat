# Personal chat pet-project

Personal pet-project I am working on for self-education purposes.

The project is a simple real-time chat on websockets.

**The project still in the active development phase and has lack of some serious features. Also, this README doesn't match to the current state of the development**

**Technologies used:**
1. [Cats](https://typelevel.org/cats/) - for writing abstract code and as a standardized interface between other libraries
2. [Cats Effect](https://typelevel.org/cats-effect/) - for abstracting over concrete IO monad
3. [Monix](https://monix.io) - concrete implementation of IO monad
4. [Redis](https://redis.io) - storage and pub-sub mechanism
5. [ScalaJS](http://scala-js.org/), [ScalaJS bindings for ReactJS](https://github.com/japgolly/scalajs-react), [Bootstrap 4](https://getbootstrap.com) and [ScalaCSS](https://github.com/japgolly/scalacss) - for web client
6. [OpenAPI](https://swagger.io) - API documentation
7. [ScalaTest](http://www.scalatest.org) with [ScalaCheck](https://www.scalacheck.org) - for integration property-based tests

I've used the [Tagless Final](https://www.becompany.ch/en/blog/2018/06/21/tagless-final) style of the functional programming, but unsure that everything is written in optimal and correct way and would be very grateful to hear any constructive critics!

## Building

`sbt appJVM/docker:publishLocal` will produce the `com.github.igorramazanov/chat` Docker image.

## Running
The `com.github.igorramazanov/chat` Docker image:
* expects: `REDIS_HOST` env var pointing to the IP address/hostname of Redis
* exposes: `8080` port for interaction

## API
2 types of API:
* **HTTP-based** [(link to the OpenAPI spec)](/openapi.yml) - for authentication and registration
* **WebSocket** - for real-time communication

At the current moment, WebSockets API is not documented yet, but you may check the [integration tests whichs shows how to interact with the Docker image](/app/jvm/src/it/scala/com/github/igorramazanov/chat/IntegrationTests.scala)

## Web Interface
[http://localhost:8080/](http://localhost:8080/)

![Web UI](/webui.png)