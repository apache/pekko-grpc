// Can be removed when we move to 2.12.14
// https://github.com/akka/akka-grpc/pull/1279
scalaVersion := "2.12.16"

resolvers += "Apache Snapshots".at("https://repository.apache.org/content/repositories/snapshots/")

organization := "com.lightbend.akka.grpc"

val grpcVersion = "1.48.1" // checked synced by VersionSyncCheckPlugin

libraryDependencies ++= Seq(
  "io.grpc" % "grpc-interop-testing" % grpcVersion % "protobuf-src",
  "com.lightbend.akka.grpc" %% "pekko-grpc-interop-tests" % sys.props("project.version") % "test",
  "org.scalatest" %% "scalatest" % "3.0.4" % "test" // ApacheV2
)

scalacOptions ++= List("-unchecked", "-deprecation", "-language:_", "-encoding", "UTF-8")

enablePlugins(PekkoGrpcPlugin)

// proto files from "io.grpc" % "grpc-interop-testing" contain duplicate Empty definitions;
// * google/protobuf/empty.proto
// * io/grpc/testing/integration/empty.proto
// They have different "java_outer_classname" options, but scalapb does not look at it:
// https://github.com/scalapb/ScalaPB/issues/243#issuecomment-279769902
// Therefore we exclude it here.
PB.generate / excludeFilter := new SimpleFileFilter((f: File) =>
  f.getAbsolutePath.endsWith("google/protobuf/empty.proto"))

//#sources-both
// This is the default - both client and server
pekkoGrpcGeneratedSources := Seq(PekkoGrpc.Client, PekkoGrpc.Server)

//#sources-both

/**
 * //#sources-client
 * // only client
 * pekkoGrpcGeneratedSources := Seq(PekkoGrpc.Client)
 *
 * //#sources-client
 *
 * //#sources-server
 * // only server
 * pekkoGrpcGeneratedSources := Seq(PekkoGrpc.Server)
 * //#sources-server
 *
 * //#languages-scala
 * // default is Scala only
 * pekkoGrpcGeneratedLanguages := Seq(PekkoGrpc.Scala)
 *
 * //#languages-scala
 *
 * //#languages-java
 * // Java only
 * pekkoGrpcGeneratedLanguages := Seq(PekkoGrpc.Java)
 *
 * //#languages-java
 */

//#languages-both
// Generate both Java and Scala API's.
// By default the 'flat_package' option is enabled so that generated
// package names are consistent between Scala and Java.
// With both languages enabled we disable that option to avoid name conflicts
pekkoGrpcGeneratedLanguages := Seq(PekkoGrpc.Scala, PekkoGrpc.Java)
pekkoGrpcCodeGeneratorSettings := pekkoGrpcCodeGeneratorSettings.value.filterNot(_ == "flat_package")
//#languages-both
