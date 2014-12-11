organization  := "com.reactmq"

name := "reactmq"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.4"

val akkaVersion = "2.3.7"

libraryDependencies ++= Seq(
  // akka
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-experimental" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-contrib" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-experimental" % "1.0-M1",
  // util
  "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2",
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  "org.scalatest" %% "scalatest" % "2.2.2" % "test",
  "joda-time" % "joda-time" % "2.5",
  "org.joda" % "joda-convert" % "1.7"
)
