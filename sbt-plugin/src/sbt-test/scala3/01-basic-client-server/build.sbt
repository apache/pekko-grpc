scalaVersion := "3.1.0"

resolvers += "Apache Snapshots".at("https://repository.apache.org/content/repositories/snapshots/")

scalacOptions += "-Xfatal-warnings"

enablePlugins(PekkoGrpcPlugin)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.9" % "test")
