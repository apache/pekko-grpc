// Can be removed when we move to 2.12.14
// https://github.com/akka/akka-grpc/pull/1279
scalaVersion := "2.12.18"

// TODO remove these resolvers when we start using released Pekko jars
resolvers += Resolver.ApacheMavenSnapshotsRepo
resolvers += "apache-staging".at("https://repository.apache.org/content/groups/staging/")

scalacOptions += "-Xfatal-warnings"

enablePlugins(PekkoGrpcPlugin)

assembly / assemblyMergeStrategy := {
  // https://github.com/akka/akka/issues/29456
  case PathList("google", "protobuf", _)    => MergeStrategy.discard
  case PathList("google", "protobuf", _, _) => MergeStrategy.discard
  case x =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}
