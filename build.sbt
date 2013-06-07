name := "AnormCypher"
 
version := "0.4.0"
 
publishMavenStyle := true

organization := "org.anormcypher"

publishTo := Some(Resolver.sftp("AnormCypher repo", "repo.anormcypher.org", "/home/wfreeman/www/repo.anormcypher.org"))

scalaVersion := "2.10.1"

scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked", "-feature")

resolvers ++= Seq("codahale" at "http://repo.codahale.com/",
  "Mandubian snapshots" at "https://github.com/mandubian/mandubian-mvn/raw/master/snapshots/",
  "Mandubian releases" at "https://github.com/mandubian/mandubian-mvn/raw/master/releases/")

parallelExecution in Test := false

//crossScalaVersions := Seq("2.9.1", "2.9.2")
 
libraryDependencies ++= Seq(
  "org.scalatest" % "scalatest_2.10" % "2.0.M6-SNAP20" % "test",
  "net.databinder.dispatch" %% "dispatch-core" % "0.10.0",
  "play" %% "play-json" % "2.2-SNAPSHOT",
  "org.neo4j" % "neo4j" % "1.9"
)

seq(lsSettings :_*)

(LsKeys.tags in LsKeys.lsync) := Seq("anorm", "cypher", "neo4j", "neo")

(externalResolvers in LsKeys.lsync) := Seq(
  "anormcypher resolver" at "http://repo.anormcypher.org")

(description in LsKeys.lsync) :=
  "A Neo4j library modeled after Play's Anorm."
