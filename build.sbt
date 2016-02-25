name := "RiskSense-AnormCypher"
 
version := "0.8.1"

publishMavenStyle := true

organization := "org.anormcypher"

publishTo := Some(Resolver.sftp("AnormCypher repo", "repo.anormcypher.org", "/home/wfreeman/www/repo.anormcypher.org"))

scalaVersion := "2.11.7"

scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked", "-feature")

resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "Bintray" at "http://dl.bintray.com/typesafe/maven-releases/com/typesafe/play/extras/"

parallelExecution in Test := false

val playVersion = "2.4.3"

libraryDependencies ++= Seq(
  "com.typesafe.play"        %% "play-json"        % playVersion,
  "com.typesafe.play"        %% "play-ws"          % playVersion,
  "com.typesafe.play"        %% "play-iteratees"   % playVersion,
  "com.typesafe.play.extras" %% "iteratees-extras" % "1.5.0",
  "org.scalatest"            %% "scalatest"        % "2.2.4" % "test",
  "org.scala-lang.modules"   %% "scala-async"      % "0.9.5"
)

seq(lsSettings :_*)

(LsKeys.tags in LsKeys.lsync) := Seq("anorm", "cypher", "neo4j", "neo")

(externalResolvers in LsKeys.lsync) := Seq(
  "anormcypher resolver" at "http://repo.anormcypher.org")

(description in LsKeys.lsync) :=
  "A Neo4j library modeled after Play's Anorm."

initialCommands in console := """
import org.anormcypher._
import play.api.libs.ws._

// Provide an instance of WSClient
val wsclient = ning.NingWSClient()

// Setup the Rest Client
implicit val connection = Neo4jREST()(wsclient)

// Provide an ExecutionContext
implicit val ec = scala.concurrent.ExecutionContext.global
"""

cleanupCommands in console := """
wsclient.close()
"""

