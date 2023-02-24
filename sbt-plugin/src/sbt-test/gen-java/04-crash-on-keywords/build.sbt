// Can be removed when we move to 2.12.14
// https://github.com/akka/akka-grpc/pull/1279
scalaVersion := "2.12.16"

resolvers += "Apache Snapshots".at("https://repository.apache.org/content/repositories/snapshots/")

enablePlugins(PekkoGrpcPlugin)

javacOptions += "-Xdoclint:all"

pekkoGrpcGeneratedLanguages := Seq(PekkoGrpc.Java)

libraryDependencies += "com.google.protobuf" % "protobuf-java" % org.apache.pekko.grpc.gen.BuildInfo.googleProtobufVersion % "protobuf"
