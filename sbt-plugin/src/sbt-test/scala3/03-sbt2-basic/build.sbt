/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

// Verify that the pekko-grpc sbt plugin cross-builds correctly for sbt 1.x and sbt 2.x.
// When run in the Scala 3 build pass (+scripted), scriptedSbt is set to 2.x.y.
scalaVersion := "3.3.7"

enablePlugins(PekkoGrpcPlugin)
