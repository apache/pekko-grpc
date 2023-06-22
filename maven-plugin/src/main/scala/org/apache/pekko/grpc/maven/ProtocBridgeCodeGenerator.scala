/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.maven

import org.apache.pekko
import pekko.grpc.gen.Logger
import protocbridge.Artifact

/**
 * Adapts existing `org.apache.pekko.grpc.gen.CodeGenerator` into the protocbridge required type
 */
class ProtocBridgeCodeGenerator(
    impl: pekko.grpc.gen.CodeGenerator,
    scalaBinaryVersion: pekko.grpc.gen.CodeGenerator.ScalaBinaryVersion,
    logger: Logger)
    extends protocbridge.ProtocCodeGenerator {
  override def run(request: Array[Byte]): Array[Byte] = impl.run(request, logger)
  override def suggestedDependencies: Seq[Artifact] = impl.suggestedDependencies(scalaBinaryVersion)
  override def toString = s"ProtocBridgeSbtPluginCodeGenerator(${impl.name}: $impl)"
}
