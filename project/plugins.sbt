resolvers ++= Seq(
  "less is" at "http://repo.lessis.me",
  "Sonatype" at "http://oss.sonatype.org/content/repositories/releases/")

addSbtPlugin("me.lessis" % "ls-sbt" % "0.1.2")
