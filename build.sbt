import AssemblyKeys._

assemblySettings

jarName in assembly := "LDA.jar"

name := "LDA"

version := "1.0"

scalaVersion := "2.10.2"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.2.1",
  "com.typesafe.akka" %% "akka-testkit" % "2.2.1",
  "org.scalatest" %% "scalatest" % "1.9.1" % "test",
  "junit" % "junit" % "4.11" % "test",
  "com.novocode" % "junit-interface" % "0.10" % "test",
  "org.apache.commons" % "commons-math3" % "3.3",
  "net.liftweb" %% "lift-json" % "2.5+"
)

testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")