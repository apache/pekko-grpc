scalaVersion := "3.3.0"

scalacOptions += "-Xfatal-warnings"

enablePlugins(PekkoGrpcPlugin)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.15" % "test")
