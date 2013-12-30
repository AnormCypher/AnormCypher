name := "AnormCypher"
 
version := "1.0.0"
 
organization := "org.anormcypher"

scalaVersion := "2.10.3"

scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked", "-feature")

parallelExecution in Test := false

resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.0" % "test",
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.0",
  "com.typesafe.play" %% "play" % "2.2.1",
  //"com.typesafe.play" %% "play-json" % "2.2.1",
  //"com.typesafe.play" %% "play-iteratees" % "2.2.1",
  "com.typesafe.play.extras" %% "iteratees-extras" % "1.0.0",
  "org.neo4j" % "neo4j" % "2.0.0"
)

seq(lsSettings :_*)

(LsKeys.tags in LsKeys.lsync) := Seq("anorm", "cypher", "neo4j", "neo")

(description in LsKeys.lsync) :=
  "A Neo4j library modeled after Play's Anorm."
