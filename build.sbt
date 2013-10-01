name := "AnormCypher"
 
version := "0.4.2"
 
publishMavenStyle := true

organization := "org.anormcypher"

publishTo := Some(Resolver.sftp("AnormCypher repo", "repo.anormcypher.org", "/home/wfreeman/www/repo.anormcypher.org"))

scalaVersion := "2.10.2"

scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked", "-feature")

resolvers ++= Seq(
  "Mandubian snapshots" at "https://github.com/mandubian/mandubian-mvn/raw/master/snapshots/",
  "Mandubian releases" at "https://github.com/mandubian/mandubian-mvn/raw/master/releases/"
  )

parallelExecution in Test := false

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "1.9.1" % "test",
  "net.databinder.dispatch" %% "dispatch-core" % "0.9.5",
  "com.typesafe.play" %% "play-json" % "2.2.0"
)

seq(lsSettings :_*)

(LsKeys.tags in LsKeys.lsync) := Seq("anorm", "cypher", "neo4j", "neo")

(externalResolvers in LsKeys.lsync) := Seq(
  "anormcypher resolver" at "http://repo.anormcypher.org")

(description in LsKeys.lsync) :=
  "A Neo4j library modeled after Play's Anorm."
