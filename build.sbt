import Dependencies._

ThisBuild / scalaVersion := "3.3.7"
ThisBuild / version      := "0.1.0-SNAPSHOT"

ThisBuild / scalacOptions ++= Seq(
  "-no-indent",
  "-rewrite",
  "-deprecation", // Warns about deprecated APIs
  "-feature",     // Warns about advanced language features
  "-unchecked",
  // "-Wunused:imports",
  //   "-Wunused:privates",
  //   "-Wunused:locals",
  //   "-Wunused:explicits",
  //   "-Wunused:implicits",
  //   "-Wunused:params",
  //   "-Wvalue-discard",
  // "-language:strictEquality",
  "-Xmax-inlines:100000"
)

lazy val root = (project in file(".")).settings(
  name := "fs2-by-example",
  libraryDependencies ++= Seq(
    fs2,
    fs2Kafka,
    "co.fs2" %% "fs2-io" % "3.12.2",
    munit
  )
)
