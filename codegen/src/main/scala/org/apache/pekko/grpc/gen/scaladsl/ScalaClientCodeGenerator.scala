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

package org.apache.pekko.grpc.gen.scaladsl

import scala.collection.immutable
import org.apache.pekko.grpc.gen.{ BuildInfo, CodeGenerator, Logger }
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import protocbridge.Artifact
import templates.ScalaClient.txt._

trait ScalaClientCodeGenerator extends ScalaCodeGenerator {
  override def name = "pekko-grpc-scaladsl-client"

  override def perServiceContent = super.perServiceContent + generateStub

  def generateStub(logger: Logger, service: Service): immutable.Seq[CodeGeneratorResponse.File] = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(Client(service).body)
    b.setName(s"${service.packageDir}/${service.name}Client.scala")
    logger.info(s"Generating Apache Pekko gRPC client for ${service.packageName}.${service.name}")
    immutable.Seq(b.build)
  }

  override val suggestedDependencies = (scalaBinaryVersion: CodeGenerator.ScalaBinaryVersion) =>
    // TODO: remove grpc-stub dependency once we have a pekko-http based client #193
    Artifact("io.grpc", "grpc-stub", BuildInfo.grpcVersion) +: super.suggestedDependencies(scalaBinaryVersion)
}

object ScalaClientCodeGenerator extends ScalaClientCodeGenerator
