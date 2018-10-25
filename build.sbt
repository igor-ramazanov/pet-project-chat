import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}
lazy val cleanDockerImages = taskKey[Unit]("Cleans docker images with tag:none")

val compilerOptions = Seq(
  // format: off
  "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
  "-encoding", "utf-8",                // Specify character encoding used by source files.
  "-explaintypes",                     // Explain type errors in more detail.
  "-feature",                          // Emit warning and location for usages of features that should be imported explicitly.
  "-language:existentials",            // Existential types (besides wildcard types) can be written and inferred
  "-language:experimental.macros",     // Allow macro definition (besides implementation and application)
  "-language:higherKinds",             // Allow higher-kinded types
  "-language:implicitConversions",     // Allow definition of implicit functions called views
  "-unchecked",                        // Enable additional warnings where generated code depends on assumptions.
  "-Xcheckinit",                       // Wrap field accessors to throw an exception on uninitialized access.
  "-Xfatal-warnings",                  // Fail the compilation if there are any warnings.
  "-Xfuture",                          // Turn on future language features.
  "-Xlint:adapted-args",               // Warn if an argument list is modified to match the receiver.
  "-Xlint:by-name-right-associative",  // By-name parameter of right associative operator.
  "-Xlint:constant",                   // Evaluation of a constant arithmetic expression results in an error.
  "-Xlint:delayedinit-select",         // Selecting member of DelayedInit.
  "-Xlint:doc-detached",               // A Scaladoc comment appears to be detached from its element.
  "-Xlint:inaccessible",               // Warn about inaccessible types in method signatures.
  "-Xlint:infer-any",                  // Warn when a type argument is inferred to be `Any`.
  "-Xlint:missing-interpolator",       // A string literal appears to be missing an interpolator id.
  "-Xlint:nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Xlint:nullary-unit",               // Warn when nullary methods return Unit.
  "-Xlint:option-implicit",            // Option.apply used implicit view.
  "-Xlint:package-object-classes",     // Class or object defined in package object.
  "-Xlint:poly-implicit-overload",     // Parameterized overloaded implicit methods are not visible as view bounds.
  "-Xlint:private-shadow",             // A private field (or class parameter) shadows a superclass field.
  "-Xlint:stars-align",                // Pattern sequence wildcard must align with sequence component.
  "-Xlint:type-parameter-shadow",      // A local type parameter shadows a type already in scope.
  "-Xlint:unsound-match",              // Pattern match may not be typesafe.
  "-Yno-adapted-args",                 // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
  "-Ypartial-unification",             // Enable partial unification in type constructor inference
  "-Ywarn-dead-code",                  // Warn when dead code is identified.
  "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
  "-Ywarn-inaccessible",               // Warn about inaccessible types in method signatures.
  "-Ywarn-infer-any",                  // Warn when a type argument is inferred to be `Any`.
  "-Ywarn-nullary-unit",               // Warn when nullary methods return Unit.
  "-Ywarn-nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Ywarn-numeric-widen",              // Warn when numerics are widened.
  "-Ywarn-unused:implicits",           // Warn if an implicit parameter is unused.
  "-Ywarn-unused:imports",             // Warn if an import selector is not referenced.
  "-Ywarn-unused:locals",              // Warn if a local definition is unused.
  "-Ywarn-unused:params",              // Warn if a value parameter is unused.
  "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
  "-Ywarn-unused:privates",            // Warn if a private member is unused.
  "-Ywarn-value-discard"               // Warn when non-Unit expression results are unused.
  // format: on
)

scalaVersion in ThisBuild := "2.12.7"

lazy val root = project.in(file("."))

val sharedSettings = Seq(
  organization := "io.github.igorramazanov",
  libraryDependencies ++= Seq(
//    "com.github.mpilquist" %%% "simulacrum" % "0.13.0"
  ),
  resolvers += Resolver.sonatypeRepo("releases"),
  wartremoverErrors ++= Warts.unsafe,
  autoCompilerPlugins := true
)

val jvmSettings = Seq(
  name := "pet-project-chat-backend",
  mainClass := Some("io.github.igorramazanov.chat.Bootloader"),
  libraryDependencies ++= Seq(
    "com.github.mpilquist" %% "simulacrum" % "0.13.0",
    "io.monix" %% "monix" % "3.0.0-RC1",
    "eu.timepit" %% "refined" % "0.9.2",
    "com.typesafe.akka" %% "akka-http" % "10.1.5",
    "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.5" % "compile,it,test",
    ("com.github.scredis" %% "scredis" % "2.1.7")
      .exclude("com.typesafe.akka", s"akka-actor_${scalaBinaryVersion.value}"),
    "com.lihaoyi" %% "scalatags" % "0.6.7",
    "com.typesafe.akka" %% "akka-stream" % "2.5.12",
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "com.typesafe.akka" %% "akka-slf4j" % "2.5.12" % "compile,it,test",
    "com.typesafe.akka" %% "akka-http-testkit" % "10.1.5" % "it,test",
    "org.scalatest" %% "scalatest" % "3.0.5" % "it,test",
    "org.scalacheck" %% "scalacheck" % "1.14.0" % "it,test",
    "com.dimafeng" %% "testcontainers-scala" % "0.20.0" % "it,test"
  ),
  (Docker / packageName) := "com.github.igorramazanov/chat",
  dockerUpdateLatest:= true,
  dockerExposedPorts := Seq(8080),
  dockerBaseImage := "openjdk:8-jre-alpine",
  cleanDockerImages := {
    import scala.sys.process._
    ("docker images -q --filter dangling=true" #| "xargs docker rmi").!!
  },
  (Docker / publishLocal) := {
    (Docker / publishLocal).value
    cleanDockerImages.value
  },
  (IntegrationTest / test) := {
    (Docker / publishLocal).value
    cleanDockerImages.value
    (IntegrationTest / test).value
  },
  (IntegrationTest / scalacOptions) --= Seq("-Xfatal-warnings", "-deprecation"),
  Compile / scalacOptions := ("-Xplugin:" + (baseDirectory.in(root).value / ("paradise_" + scalaVersion.value + "-2.1.1.jar")).absolutePath) +: compilerOptions
) ++ Defaults.itSettings

val jsSettings = Seq(
  name := "pet-project-chat-frontend",
  libraryDependencies ++= Seq(
    "com.github.japgolly.scalajs-react" %%% "core" % "1.3.1",
    "com.github.japgolly.scalajs-react" %%% "extra" % "1.3.1"
  ),
  (Compile / npmDependencies) ++= Seq(
    "react" -> "16.5.1",
    "react-dom" -> "16.5.1"),
  scalaJSUseMainModuleInitializer := true,
  (Compile / scalacOptions) ++= compilerOptions.filterNot(Set("-Ywarn-unused:params", "-Ywarn-value-discard").apply)
)


lazy val app = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(sharedSettings: _*)
  .jvmSettings(jvmSettings: _*)
  .jsSettings(jsSettings: _*)

lazy val appJs = app.js.enablePlugins(ScalaJSBundlerPlugin)
lazy val appJvm = app.jvm
  .configs(IntegrationTest)
  .enablePlugins(JavaServerAppPackaging, AshScriptPlugin, DockerPlugin)
  .settings((Compile / resources) ++= {
    val js = (appJs / Compile / fastOptJS / webpack).value.map(_.data)
//    val sourceMap = new File(js.getAbsolutePath + ".map")
//    Seq(js, sourceMap)
    js
  })
