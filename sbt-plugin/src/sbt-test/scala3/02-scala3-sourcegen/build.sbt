/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

scalaVersion := sys.props.getOrElse(
  "pekko.grpc.scala3.next.version",
  sys.error("pekko.grpc.scala3.next.version must be provided by scriptedLaunchOpts"))

scalacOptions ++= Seq(
  "-Werror",
  "-Wconf:msg=Implicit parameters should be provided with a `using` clause:s")

enablePlugins(PekkoGrpcPlugin)

resolvers += Resolver.ApacheMavenSnapshotsRepo

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.20" % Test)
