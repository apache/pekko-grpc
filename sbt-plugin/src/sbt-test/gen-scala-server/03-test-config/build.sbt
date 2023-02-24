// Can be removed when we move to 2.12.14
// https://github.com/akka/akka-grpc/pull/1279
scalaVersion := "2.12.16"

resolvers += "Apache Snapshots".at("https://repository.apache.org/content/repositories/snapshots/")

enablePlugins(PekkoGrpcPlugin)

Compile / pekkoGrpcGeneratedSources := Seq(PekkoGrpc.Server)

//#test
Test / pekkoGrpcGeneratedSources := Seq(PekkoGrpc.Client)
Test / PB.protoSources ++= (Compile / PB.protoSources).value
//#test

//#it
configs(IntegrationTest)
Defaults.itSettings
PekkoGrpcPlugin.configSettings(IntegrationTest)

IntegrationTest / pekkoGrpcGeneratedLanguages := Seq(PekkoGrpc.Java)
IntegrationTest / PB.protoSources ++= (Compile / PB.protoSources).value
//#it
