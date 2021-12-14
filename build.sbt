name := "actorika"

version := "0.0.1.10-SNAPSHOT"

val scala213 = "2.13.6"
val scala3 = "3.0.0"

scalaVersion := scala213

val versions = Seq(scala213, scala3)

organization := "io.github.truerss"

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

sonatypeRepository := "https://s01.oss.sonatype.org/service/local"
sonatypeCredentialHost := "s01.oss.sonatype.org"

ThisBuild / publishTo := {
  val nexus = "https://s01.oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots/")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

crossScalaVersions := versions

homepage := Some(url("https://github.com/truerss/truerss"))
scmInfo := Some(ScmInfo(url("https://github.com/truerss/actorika"), "git@github.com:truerss/content-extractor.git"))
developers := List(Developer("mike", "mike", "mike.fch1@gmail.com", url("https://github.com/fntz")))
licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

publishMavenStyle := true

Test / parallelExecution := false

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-api" % "1.7.30" % s"$Test,$Provided",
  "ch.qos.logback" % "logback-classic" % "1.2.3" % s"$Test,$Provided",
  "org.scalameta" %% "munit" % "0.7.26" % Test
)
