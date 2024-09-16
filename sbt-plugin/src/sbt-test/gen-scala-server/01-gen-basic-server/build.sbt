/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

// Can be removed when we move to 2.12.14
// https://github.com/akka/akka-grpc/pull/1279
scalaVersion := "2.12.20"

scalacOptions += "-Xfatal-warnings"

enablePlugins(PekkoGrpcPlugin)

assembly / assemblyMergeStrategy := {
  // https://github.com/akka/akka/issues/29456
  case PathList("google", "protobuf", _*) => MergeStrategy.discard
  case PathList("META-INF", _*)           => MergeStrategy.discard
  case x =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}
