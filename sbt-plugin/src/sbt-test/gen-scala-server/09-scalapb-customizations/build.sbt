// Can be removed when we move to 2.12.14
// https://github.com/akka/akka-grpc/pull/1279
scalaVersion := "2.12.16"

resolvers += "Apache Snapshots".at("https://repository.apache.org/content/repositories/snapshots/")

enablePlugins(PekkoGrpcPlugin)

// Don't enable it flat_package globally, but via a package-level option instead (see package.proto)
pekkoGrpcCodeGeneratorSettings -= "flat_package"
