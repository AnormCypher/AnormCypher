name := "AnormCypher"
 
version := "0.1"
 
scalaVersion := "2.9.2"

//scalacOptions ++= Seq("-unchecked", "-deprecation")

resolvers += "codahale" at "http://repo.codahale.com/"
 
libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "1.8" % "test",
  "net.databinder.dispatch" %% "dispatch-core" % "0.9.3",
  "com.codahale" % "jerkson_2.9.1" % "0.5.0",
  "com.fasterxml.jackson.module" % "jackson-module-scala" % "2.1.1"
)
