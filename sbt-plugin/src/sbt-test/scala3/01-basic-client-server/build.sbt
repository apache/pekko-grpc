scalaVersion := "3.3.0"

resolvers += "Apache Snapshots".at("https://repository.apache.org/content/repositories/snapshots/")

scalacOptions += "-Xfatal-warnings"

enablePlugins(PekkoGrpcPlugin)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.15" % "test")
