scalaVersion := "3.3.0"

resolvers += Resolver.ApacheMavenSnapshotsRepo

scalacOptions += "-Xfatal-warnings"

enablePlugins(PekkoGrpcPlugin)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.15" % "test")
