import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}
lazy val cleanDockerImages = taskKey[Unit]("Cleans docker images with tag:none")

name := "pet-project-chat"

def inDevMode =
  sys.props.get("dev.mode").exists(value => value.equalsIgnoreCase("true"))

scalaVersion in ThisBuild := "2.13.2"
version in ThisBuild := "1.0"

lazy val root = project.in(file("."))
lazy val IntTest = config("it") extend Test

val CirceVersion = "0.13.0"
val AkkaHttpVersion = "10.1.11"
val AkkaVersion = "2.6.5"
val ScalaJsReactVersion = "1.7.0"
val ReactVersion = "16.13.1"

val sharedSettings = Seq(
  organization := "com.github.igorramazanov",
  libraryDependencies ++= Seq(
    "io.circe" %%% "circe-core" % CirceVersion,
    "io.circe" %%% "circe-parser" % CirceVersion,
    "io.circe" %%% "circe-generic" % CirceVersion
  ),
  resolvers ++= Seq(Resolver.sonatypeRepo("releases")),
//  wartremoverErrors ++= Warts.unsafe,
  autoCompilerPlugins := true,
  scalacOptions += "-Ymacro-annotations"
)

val jvmSettings = Seq(
  name := "pet-project-chat-backend",
  mainClass := Some("com.github.igorramazanov.chat.MainBackend"),
  libraryDependencies ++= Seq(
    "org.typelevel" %% "simulacrum" % "1.0.0",
    "io.monix" %% "monix" % "3.2.1",
    "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
    "com.lihaoyi" %% "scalatags" % "0.9.1",
    "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
    "com.github.scredis" %% "scredis" % "2.3.3",
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "com.github.scopt" %% "scopt" % "3.7.1",
    "com.sun.mail" % "javax.mail" % "1.6.2",
    "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
    "org.scalatest" %% "scalatest" % "3.0.8" % "it,test",
    "org.scalacheck" %% "scalacheck" % "1.14.3" % "it,test",
    "com.dimafeng" %% "testcontainers-scala" % "0.37.0" % "it,test"
  ),
  (Docker / packageName) := "com.github.igorramazanov/chat",
  dockerUpdateLatest := true,
  dockerExposedPorts := Seq(8080),
  dockerBaseImage := "openjdk:8-jre-alpine",
  cleanDockerImages := {
    import scala.sys.process._
    ("docker images -q --filter dangling=true" #| "xargs docker rmi").!!
  },
  Test / testOptions := Seq(
    Tests.Filter(s => s.endsWith("Test") && !s.endsWith("ITest"))
  ),
  IntTest / testOptions := Seq(Tests.Filter(_.endsWith("ITest")))
)

val jsSettings = Seq(
  name := "pet-project-chat-frontend",
  libraryDependencies ++= Seq(
    "com.github.japgolly.scalajs-react" %%% "core" % ScalaJsReactVersion,
    "com.github.japgolly.scalajs-react" %%% "extra" % ScalaJsReactVersion,
    "com.github.japgolly.scalacss" %%% "ext-react" % "0.6.1"
  ),
  jsDependencies ++= Seq(
    "org.webjars.npm" % "react" % ReactVersion
      / "umd/react.development.js"
      minified "umd/react.production.min.js"
      commonJSName "React",
    "org.webjars.npm" % "react-dom" % ReactVersion
      / "umd/react-dom.development.js"
      minified "umd/react-dom.production.min.js"
      dependsOn "umd/react.development.js"
      commonJSName "ReactDOM",
    "org.webjars.npm" % "react-dom" % ReactVersion
      / "umd/react-dom-server.browser.development.js"
      minified "umd/react-dom-server.browser.production.min.js"
      dependsOn "umd/react-dom.development.js"
  ),
  dependencyOverrides += "org.webjars.npm" % "js-tokens" % "5.0.0",
  scalaJSUseMainModuleInitializer := true
)

lazy val app = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(sharedSettings: _*)
  .jvmSettings(jvmSettings: _*)
  .jsSettings(jsSettings: _*)

lazy val appJs = app.js.enablePlugins(JSDependenciesPlugin)
lazy val appJvm = app.jvm
  .configs(IntTest)
  .enablePlugins(
    JavaServerAppPackaging,
    AshScriptPlugin,
    DockerPlugin,
    BuildInfoPlugin
  )
  .settings(
    addFrontendResourcesToClasspath()
  )
  .settings(addBuildInfoKeys(): _*)
  .settings(inConfig(IntTest)(Defaults.testTasks))

def addBuildInfoKeys() =
  if (inDevMode)
    Seq(
      buildInfoPackage := "com.github.igorramazanov.chat",
      buildInfoKeys ++= Seq[BuildInfoKey](
        name,
        version,
        scalaVersion,
        sbtVersion,
        "mode" -> "dev"
      )
    )
  else
    Seq(
      buildInfoPackage := "com.github.igorramazanov.chat",
      buildInfoKeys ++= Seq[BuildInfoKey](
        name,
        version,
        scalaVersion,
        sbtVersion,
        "mode" -> "prod"
      )
    )

def addFrontendResourcesToClasspath() =
  if (inDevMode)
    (Compile / resources) ++= {
      val js = (appJs / Compile / fastOptJS).value
      val sourceMap = new File(js.data.absolutePath + ".map")
      val jsDependencies =
        new File(
          js.data.getParentFile.absolutePath + s"/pet-project-chat-frontend-jsdeps.js"
        )
      Seq(js.data, sourceMap, jsDependencies)
    }
  else
    (Compile / resources) ++= {
      val js = (appJs / Compile / fullOptJS).value
      val jsDependencies =
        new File(
          js.data.getParentFile.absolutePath + s"/pet-project-chat-frontend-jsdeps.min.js"
        )
      Seq(js.data, jsDependencies)
    }
