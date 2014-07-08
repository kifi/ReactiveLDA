import AssemblyKeys._

assemblySettings

jarName in assembly := "LDA.jar"

name := "LDA"

version := "1.0"

scalaVersion := "2.10.2"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.4",
  "org.apache.commons" % "commons-math3" % "3.3",
  "net.liftweb" %% "lift-json" % "2.5+"
)
