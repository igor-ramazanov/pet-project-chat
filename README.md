# Personal chat pet-project

Personal pet-project I am working on for self-education purposes.

The project is a simple real-time chat on websockets.

**Technologies used:**
1. [Cats](https://typelevel.org/cats/) - for writing abstract code and as a standardized interface between other libraries
2. [Cats Effect](https://typelevel.org/cats-effect/) - for abstracting over concrete IO monad
3. [Monix](https://monix.io) - concrete implementation of IO monad
4. [Redis](https://redis.io) - storage and pub-sub mechanism
5. [ScalaJS](http://scala-js.org/), [ScalaJS bindings for ReactJS](https://github.com/japgolly/scalajs-react), [Bootstrap 4](https://getbootstrap.com) and [ScalaCSS](https://github.com/japgolly/scalacss) - for web client
6. [OpenAPI](https://swagger.io) - API documentation

I've used the [Tagless Final](https://www.becompany.ch/en/blog/2018/06/21/tagless-final) style of the functional programming, but unsure that everything is written in optimal and correct way and would be very grateful to hear any constructive critics!

## Configuration
```
Usage: pet-project-chat [options]

  --help                   prints this help
  -h, --redis-host <value>
                           (required) host of redis used as a storage
  -l, --log-level <value>  (optional) log level, must be one of 'OFF','ERROR','WARN','INFO','DEBUG','TRACE','ALL', default is INFO
  -t, --email-verification-timeout <value>
                           (optional) email verification timeout, examples are '1 second', '9 days', '3 hours', '1 hour', default is '1 day'
  -p, --verification-email-link-prefix <value>
                           (optional) prefix of the verification link sent to clients on registration, specify address by which the server is accessible, i.e. 'http://localhost:8080', if not provided then email verification on sign up be disabled
  -e, --gmail-verification-email-sender <value>
                           (optional) gmail address of verification email sender, if not provided then email verification on sign up be disabled
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
1. You'll need a Gmail account, it will be used with Google GMail API to send registration verification emails
2. Get the `credentials.json` according to [Step#1 from instructions](https://developers.google.com/gmail/api/quickstart/java)
3. Safe the downloaded `credentials.json` into `app/jvm/src/main/resources`
4. Build the project Docker image `sbt appJVM/docker:publishLocal`
5. Edit the [docker-compose-with-email-verification.ym](/docker-compose-with-email-verification.yml) replacing the `-e` option to your GMail address account
6. Run the project and Redis Docker images by `docker-compose -f docker-compose-with-email-verification.ym up`
7. Open [http://localhost:8080](http://localhost:8080) in your browser
8. On the first signing up the app will print to logs something like that (see below), follow that instructions
```
chat_1   | Please open the following address in your browser:
chat_1   |   https://accounts.google.com/o/oauth2/auth?access_type=offline&client_id=79839010810-nj08luifjin39dv08opfpd5q2phl08oo.apps.googleusercontent.com&redirect_uri=http://localhost:8888/Callback&response_type=code&scope=https://www.googleapis.com/auth/gmail.send
```

![Web UI](/webui.png)