# Personal chat pet-project
[![Build Status](https://travis-ci.org/igor-ramazanov/pet-project-chat.svg?branch=master)](https://travis-ci.org/igor-ramazanov/pet-project-chat)

Personal pet-project I am working on for self-education purposes.

The project is a simple real-time chat on websockets.

**Technologies used:**
1. [Cats](https://typelevel.org/cats/) - for writing abstract code and as a standardized interface between other libraries
2. [Cats Effect](https://typelevel.org/cats-effect/) - for abstracting over concrete IO monad
3. [Monix](https://monix.io) - concrete implementation of IO monad
4. [Redis](https://redis.io) - storage and pub-sub mechanism
5. [ScalaJS](http://scala-js.org/), [ScalaJS bindings for ReactJS](https://github.com/japgolly/scalajs-react), [Bootstrap 4](https://getbootstrap.com) and [ScalaCSS](https://github.com/japgolly/scalacss) - for web client
6. [OpenAPI](https://swagger.io) - API documentation
7. [Testcontainers](https://github.com/testcontainers/testcontainers-scala), [ScalaTest](http://www.scalatest.org) and [ScalaCheck](https://www.scalacheck.org) - [property-based integration tests](/app/jvm/src/test/scala/integration/ITest.scala)

I've used the [Tagless Final](https://www.becompany.ch/en/blog/2018/06/21/tagless-final) style of the functional programming, but unsure that everything is written in optimal and correct way and would be very grateful to hear any constructive critics!

## Configuration
```
Usage: pet-project-chat [options]

   --help                   prints this help
   --redis-host <value>     (required) host of redis used as a storage
   --log-level <value>      (optional) log level, must be one of 'OFF','ERROR','WARN','INFO','DEBUG','TRACE','ALL', default is INFO
   --smtp-host <value>      (optional) SMTP host, if not provided then verification of emails be disabled
   --smtp-port <value>      (optional) SMTP port, if not provided then verification of emails be disabled
   --smtp-tls               (optional) Use TLS for SMTP
   --smtp-from <value>      (optional) 'from' field of verification emails, if not provided then verification of emails be disabled
   --smtp-user <value>      (optional) User for SMTP server
   --smtp-password <value>  (optional) Password for SMTP server
   --verification-link-prefix <value>
                            (optional) Prefix of email verification link, i.e. 'http://localhost:8080', if not provided then verification of emails be disabled
   --email-verification-timeout <value>
                            (optional) email verification timeout, examples are '1 second', '9 days', '3 hours', '1 hour', default is '1 day'
```

## Building and running locally

#### Prerequisites
* JDK 8
* [Scala Built Tool](https://www.scala-sbt.org)
* [Docker](https://www.docker.com) 
* [Docker Compose](https://docs.docker.com/compose/) 

#### Without email verification on a new user registration
1. Build the project Docker image `sbt appJVM/docker:publishLocal`
2. Run the project and Redis Docker images by `docker-compose -f docker-compose-without-email-verification.ym up`
3. Open [http://localhost:8080](http://localhost:8080) in your browser

#### With email verification on a new user registration
1. You'll need an SMTP server, find information with your mail provider, examples: [GMail](https://support.google.com/mail/answer/7126229?visit_id=636774483047111987-1658076072&hl=en&rd=1), [MailRu](https://help.mail.ru/mail-help/mailer/popsmtp)
2. Build the project Docker image `sbt appJVM/docker:publishLocal`
3. Edit the [docker-compose-with-email-verification.ym](/docker-compose-with-email-verification.yml) replacing SMTP information you've got from the 1st step
4. Run the project and Redis Docker images by `docker-compose -f docker-compose-with-email-verification.ym up`
5. Open [http://localhost:8080](http://localhost:8080) in your browser

#### Building native executable ([is not supported at the moment](https://github.com/igor-ramazanov/pet-project-chat/issues/8))
You'll need the installed [GraalVM](http://graalvm.org/) and [Scala Built Tool](https://www.scala-sbt.org).

Execute the script in the root project dir:
```bash
./graal-build-native-executable.sh
```

![Web UI](/webui.png)