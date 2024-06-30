/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

enablePlugins(PekkoGrpcPlugin)

run / javaOptions ++= List("-Xms1g", "-Xmx1g", "-XX:+PrintGCDetails", "-XX:+PrintGCTimeStamps")

// generate both client and server (default) in Java
pekkoGrpcGeneratedLanguages := Seq(PekkoGrpc.Java)

val grpcVersion = "1.64.1" // checked synced by VersionSyncCheckPlugin

val runtimeProject = ProjectRef(file("../"), "runtime")

val codeGenProject = ProjectRef(file("../"), "codegen")

val root = project
  .in(file("."))
  .dependsOn(runtimeProject)
  // Use this instead of above when importing to IDEA, after publishLocal and replacing the version here
  /*
  .settings(libraryDependencies ++= Seq(
    "org.apache.pekko" %% "pekko-grpc-runtime" % "0.1+32-fd597fcb+20180618-1248"
  ))
   */
  .settings(
    libraryDependencies ++= Seq(
      "io.grpc" % "grpc-testing" % grpcVersion,
      "org.hdrhistogram" % "HdrHistogram" % "2.1.12",
      "org.apache.commons" % "commons-math3" % "3.6.1",
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "org.scalatestplus" %% "junit-4-13" % "3.2.19.0" % Test),
    PB.artifactResolver := PB.artifactResolver.dependsOn(codeGenProject / Compile / publishLocal).value)

compile / javacOptions += "-Xlint:deprecation"
