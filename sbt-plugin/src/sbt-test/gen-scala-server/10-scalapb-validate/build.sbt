// Can be removed when we move to 2.12.14
// https://github.com/akka/akka-grpc/pull/1279
scalaVersion := "2.12.16"

resolvers += "Apache Snapshots".at("https://repository.apache.org/content/repositories/snapshots/")

//#setup
import scalapb.GeneratorOption._

enablePlugins(PekkoGrpcPlugin)

libraryDependencies +=
  "com.thesamet.scalapb" %% "scalapb-validate-core" % scalapb.validate.compiler.BuildInfo.version % "protobuf"
Compile / PB.targets +=
  scalapb.validate.gen(FlatPackage) -> (Compile / pekkoGrpcCodeGeneratorSettings / target).value
//#setup
