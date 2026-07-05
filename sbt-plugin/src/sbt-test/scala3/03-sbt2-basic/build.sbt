/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

// Verify that the pekko-grpc sbt plugin cross-builds correctly for sbt 2.x.
scalaVersion := sys.props.getOrElse(
  "pekko.grpc.scala3.next.version",
  sys.error("pekko.grpc.scala3.next.version must be provided by scriptedLaunchOpts"))

scalacOptions ++= Seq(
  "-Wconf:msg=Implicit parameters should be provided with a `using` clause:s",
  "-Wconf:msg=Ignoring \\[this\\] qualifier:s",
  "-Wconf:msg=`_` is deprecated for wildcard arguments of types:s")

enablePlugins(PekkoGrpcPlugin)
