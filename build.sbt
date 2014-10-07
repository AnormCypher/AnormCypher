name := "AnormCypher"
 
version := "0.6.0"
 
publishMavenStyle := true

organization := "org.anormcypher"

publishTo := Some(Resolver.sftp("AnormCypher repo", "repo.anormcypher.org", "/home/wfreeman/www/repo.anormcypher.org"))

scalaVersion := "2.11.0"

scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked", "-feature")

resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"

parallelExecution in Test := false

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.1.5" % "test",
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.1",
  "com.typesafe.play" %% "play-json" % "2.3.5"
)

seq(lsSettings :_*)

(LsKeys.tags in LsKeys.lsync) := Seq("anorm", "cypher", "neo4j", "neo")

(externalResolvers in LsKeys.lsync) := Seq(
  "anormcypher resolver" at "http://repo.anormcypher.org")

(description in LsKeys.lsync) :=
  "A Neo4j library modeled after Play's Anorm."
