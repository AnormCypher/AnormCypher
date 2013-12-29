resolvers ++= Seq(
  "less is" at "http://repo.lessis.me",
  "scct-github-repository" at "http://mtkopone.github.com/scct/maven-repo",
  "Sonatype" at "http://oss.sonatype.org/content/repositories/releases/")

addSbtPlugin("me.lessis" % "ls-sbt" % "0.1.2")

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.5.1")

addSbtPlugin("reaktor" % "sbt-scct" % "0.2-SNAPSHOT")

addSbtPlugin("com.github.theon" %% "xsbt-coveralls-plugin" % "0.0.4")
