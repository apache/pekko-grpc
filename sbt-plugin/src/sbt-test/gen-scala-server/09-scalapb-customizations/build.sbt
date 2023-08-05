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
scalaVersion := "2.12.18"

enablePlugins(PekkoGrpcPlugin)

// Don't enable it flat_package globally, but via a package-level option instead (see package.proto)
pekkoGrpcCodeGeneratorSettings -= "flat_package"
