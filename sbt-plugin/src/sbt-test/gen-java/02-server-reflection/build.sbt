// Can be removed when we move to 2.12.14
// https://github.com/akka/akka-grpc/pull/1279
scalaVersion := "2.12.18"

// TODO remove these resolvers when we start using released Pekko jars
resolvers += Resolver.ApacheMavenSnapshotsRepo

enablePlugins(PekkoGrpcPlugin)

pekkoGrpcGeneratedLanguages := Seq(PekkoGrpc.Java)
