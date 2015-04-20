addSbtPlugin("com.twitter" %% "scrooge-sbt-plugin" % "3.16.3")

// The Typesafe repository
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.3.10")

resolvers += "Sonatype OSS Releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2"

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.6.0")