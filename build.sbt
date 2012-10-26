name := "neo4j-scala-rest"
 
version := "0.1"
 
scalaVersion := "2.9.1"
 
libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "1.6.1" % "test"
)

libraryDependencies += "net.databinder.dispatch" %% "dispatch-core" % "0.9.2"

libraryDependencies += "junit" % "junit" % "4.10" % "test"

libraryDependencies += "com.novocode" % "junit-interface" % "0.8" % "test->default"
