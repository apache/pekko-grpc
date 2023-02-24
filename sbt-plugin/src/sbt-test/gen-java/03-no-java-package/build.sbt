// Can be removed when we move to 2.12.14
// https://github.com/akka/akka-grpc/pull/1279
scalaVersion := "2.12.16"

resolvers += "Apache Snapshots".at("https://repository.apache.org/content/repositories/snapshots/")

enablePlugins(PekkoGrpcPlugin)

pekkoGrpcGeneratedLanguages := Seq(PekkoGrpc.Java)
