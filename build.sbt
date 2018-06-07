ThisBuild / resolvers ++= Seq(
    "Apache Development Snapshot Repository" at "https://repository.apache.org/content/repositories/snapshots/",
    Resolver.mavenLocal
)

ThisBuild / scalaVersion := "2.11.12"

lazy val settings = Seq(
  organization := "net.vankaam",
  version := "1.0",
  scalaVersion := "2.11.11",
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature"
  )
)

name := "FlinkWebSocketSource"


lazy val dependencies = new {
  val flinkV = "1.5.0"

  val parserCombinatorV = "1.1.0"
  val scalaAsyncV = "0.9.7"
  val scalaJavaCompatV = "0.8.0"
  val shapelessV = "2.3.3"
  val scalaTimeV = "0.4.1"
  val rxV = "0.26.5"
  val logBackV = "1.1.7"
  val scalaLoggingV = "3.5.0"
  val typesafeConfigV = "1.3.1"
  val json4sV = "3.6.0-M2"


  val mockitoV = "2.13.0"
  val scalaTestV = "3.0.1"


  val flinkScala             = "org.apache.flink"  %% "flink-scala"            % flinkV       % "provided"
  val flinkStreamingScala   = "org.apache.flink"  %% "flink-streaming-scala"  % flinkV       % "provided"
  val flinkTable             = "org.apache.flink"  %% "flink-table"            % flinkV       % "provided"

  val parserCombinator = "org.scala-lang.modules" %% "scala-parser-combinators" % parserCombinatorV
  val scalaAsync = "org.scala-lang.modules" %% "scala-async" % scalaAsyncV
  val scalaJavaCompat = "org.scala-lang.modules" % "scala-java8-compat_2.11" % scalaJavaCompatV
  val shapeless = "com.chuusai" %% "shapeless" % shapelessV
  val scalaTime =  "codes.reactive" %% "scala-time" % scalaTimeV
  val rx = "io.reactivex" %% "rxscala" % rxV
  val logBack =  "ch.qos.logback" % "logback-classic" % logBackV
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV
  val json4sNative = "org.json4s" %% "json4s-native" % json4sV

  val typeSafeConfig = "com.typesafe" % "config" % typesafeConfigV

  val mockito = "org.mockito" % "mockito-core" % mockitoV % "test"
  val scalactic = "org.scalactic" %% "scalactic" % scalaTestV % "test"
  val scalaTest = "org.scalatest" %% "scalatest" % scalaTestV % "test"
}



lazy val commonDependencies = Seq(
  //Libraries
  dependencies.parserCombinator,
  dependencies.scalaAsync,
  dependencies.scalaJavaCompat,
  //dependencies.shapeless,
  dependencies.scalaTime,
  dependencies.rx,
  dependencies.logBack,
  dependencies.scalaLogging,
  //dependencies.json4sNative,
  dependencies.typeSafeConfig,

  //Test
  dependencies.mockito,
  dependencies.scalactic,
  dependencies.scalaTest
)


lazy val flinkDependencies = Seq(
  dependencies.flinkScala,
  dependencies.flinkStreamingScala,
  dependencies.flinkTable
)

lazy val root = (project in file(".")).
  settings(settings,
    libraryDependencies ++= flinkDependencies ++ commonDependencies
  )

assembly / mainClass := Some("net.vankaam.Job")

// make run command include the provided dependencies
Compile / run  := Defaults.runTask(Compile / fullClasspath,
                                   Compile / run / mainClass,
                                   Compile / run / runner
                                  ).evaluated

// stays inside the sbt console when we press "ctrl-c" while a Flink programme executes with "run" or "runMain"
Compile / run / fork := true
Global / cancelable := true

assemblyMergeStrategy in assembly := {
  case PathList("com","typesafe","config", xs @ _*) => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

// exclude Scala library from assembly
assembly / assemblyOption  := (assembly / assemblyOption).value.copy(includeScala = false)


test in assembly := {}