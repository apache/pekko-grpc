scalaVersion := "3.3.0"

// TODO remove these resolvers when we start using released Pekko jars
resolvers += Resolver.ApacheMavenSnapshotsRepo
resolvers += "apache-staging".at("https://repository.apache.org/content/groups/staging/")

scalacOptions += "-Xfatal-warnings"

enablePlugins(PekkoGrpcPlugin)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.15" % "test")
