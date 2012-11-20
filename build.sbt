name := "AnormCypher"
 
version := "0.2.1"
 
publishMavenStyle := true

organization := "org.anormcypher"

publishTo := Some(Resolver.sftp("AnormCypher repo", "repo.anormcypher.org", "/home/wfreeman/www/repo.anormcypher.org"))

scalaVersion := "2.9.2"

resolvers += "codahale" at "http://repo.codahale.com/"

parallelExecution in Test := false

crossScalaVersions := Seq("2.9.1", "2.9.2")
 
libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "1.8" % "test",
  "net.databinder.dispatch" %% "dispatch-core" % "0.9.3",
  "com.codahale" % "jerkson_2.9.1" % "0.5.0"
)
