import sbt._
import sbt.Keys._
import com.twitter.scrooge.ScroogeSBT
import play.PlayScala
import play.PlayImport._
import com.typesafe.sbt.SbtScalariform

object ZipkinInterrogator extends Build {

  lazy val theVersion = "1.0.0-SNAPSHOT"
  lazy val theScalaVersion = "2.11.7"
  lazy val zipkinVersion = "1.4.0" // Moving to 1.5.0 leads to source incompatibility

  lazy val root = Project(id = "zipkin-interrogator", base = file("."), settings = commonSettings ++ ScroogeSBT.newSettings)
    .enablePlugins(PlayScala)
    .settings(
      name := "zipkin-interrogator",
      libraryDependencies ++= Seq(
        cache,
        "org.apache.thrift" % "libthrift" % "0.9.2",
        "io.zipkin" % "zipkin-common" % zipkinVersion, // this dependency is currently hard-set to use Scala 2.11.x
        "io.zipkin" % "zipkin-scrooge" % zipkinVersion,
        "com.twitter" %% "algebird-core" % "0.11.0",
        "org.ocpsoft.prettytime" % "prettytime" % "4.0.0.Final",
        "com.github.nscala-time" %% "nscala-time" % "1.8.0",
        "org.webjars" %% "webjars-play" % "2.3.0-2",
        "org.webjars" % "bootstrap" % "3.3.4",
        "org.webjars" % "html5shiv" % "3.7.2",
        "org.webjars" % "respond" % "1.4.2",
        "org.webjars" % "holderjs" % "2.5.2",
        "org.scalatest" %% "scalatest" % "2.2.4" % Test
      ),
      ScroogeSBT.scroogeThriftSourceFolder in Compile <<= (baseDirectory in ThisBuild)
        (_ / "zipkin-thrift" / "src" / "main" / "thrift" / "com" / "twitter" / "zipkin" ),
      ScroogeSBT.scroogeThriftOutputFolder in Compile <<= (sourceManaged)
        (_ / ".." /"scrooge-sources"  ),
      ScroogeSBT.scroogeBuildOptions in Compile := List("--finagle")
    )

  lazy val commonSettings = Seq(
    organization := "com.m3",
    version := theVersion,
    scalaVersion := theScalaVersion
  ) ++
    compilerSettings ++
    SbtScalariform.scalariformSettings

  lazy val compilerSettings = Seq(
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xlint", "-Xlog-free-terms")
  )


}
