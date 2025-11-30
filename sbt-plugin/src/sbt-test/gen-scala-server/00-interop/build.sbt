/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

organization := "org.apache.pekko"

scalaVersion := "2.13.17"

val grpcVersion = "1.77.0" // checked synced by VersionSyncCheckPlugin

libraryDependencies ++= Seq(
  "io.grpc" % "grpc-interop-testing" % grpcVersion % "protobuf-src",
  "org.apache.pekko" %% "pekko-grpc-interop-tests" % sys.props("project.version") % "test",
  "org.scalatest" %% "scalatest" % "3.2.19" % Test)

scalacOptions ++= List("-unchecked", "-deprecation", "-language:_", "-encoding", "UTF-8")

enablePlugins(PekkoGrpcPlugin)

// proto files from "io.grpc" % "grpc-interop-testing" contain duplicate Empty definitions;
// * google/protobuf/empty.proto
// * io/grpc/testing/integration/empty.proto
// They have different "java_outer_classname" options, but scalapb does not look at it:
// https://github.com/scalapb/ScalaPB/issues/243#issuecomment-279769902
// Therefore we exclude it here.
PB.generate / excludeFilter := new SimpleFileFilter(f => {
  val path = f.getAbsolutePath
  val ps = java.io.File.pathSeparator
  path.contains("envoy") || path.endsWith(s"google${ps}protobuf${ps}empty.proto")
})

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
