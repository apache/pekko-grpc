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

package org.apache.pekko.grpc.gen.javadsl

import org.apache.pekko.grpc.gen.{ BuildInfo, CodeGenerator, Logger }
import com.google.protobuf.compiler.PluginProtos.{ CodeGeneratorRequest, CodeGeneratorResponse }
import protocbridge.Artifact
import protocgen.CodeGenRequest
import templates.JavaCommon.txt.ApiInterface

import scala.annotation.nowarn
import scala.collection.immutable
import scala.jdk.CollectionConverters._

abstract class JavaCodeGenerator extends CodeGenerator {

  /** Override this to add generated files per service */
  def perServiceContent: Set[(Logger, Service) => immutable.Seq[CodeGeneratorResponse.File]] = Set.empty

  /** Override these to add service-independent generated files */
  def staticContent(@nowarn("msg=is never used") logger: Logger): Set[CodeGeneratorResponse.File] =
    Set.empty
  def staticContent(
      @nowarn("msg=is never used") logger: Logger,
      @nowarn("msg=is never used") allServices: Seq[Service]): Set[CodeGeneratorResponse.File] =
    Set.empty

  override def run(request: CodeGeneratorRequest, logger: Logger): CodeGeneratorResponse = {
    val b = CodeGeneratorResponse.newBuilder
    b.setSupportedFeatures(CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL.getNumber)

    // generate services code here, the data types we want to leave to scalapb

    // Currently per-invocation options, intended to become per-service options eventually
    // https://github.com/akka/akka-grpc/issues/451
    val params = request.getParameter.toLowerCase
    val serverPowerApi = params.contains("server_power_apis") && !params.contains("server_power_apis=false")
    val usePlayActions = params.contains("use_play_actions") && !params.contains("use_play_actions=false")

    val codeGenRequest = CodeGenRequest(request)
    val services = (for {
      fileDesc <- codeGenRequest.filesToGenerate
      serviceDesc <- fileDesc.getServices.asScala
    } yield Service(codeGenRequest, fileDesc, serviceDesc, serverPowerApi, usePlayActions)).toVector

    for {
      service <- services
      generator <- perServiceContent
      generated <- generator(logger, service)
    } {
      b.addFile(generated)
    }

    staticContent(logger).map(b.addFile)
    staticContent(logger, services).map(b.addFile)

    b.build()
  }

  def generateServiceInterface(service: Service): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(ApiInterface(service).body)
    b.setName(s"${service.packageDir}/${service.name}.java")
    b.build
  }

  override val suggestedDependencies = (scalaBinaryVersion: CodeGenerator.ScalaBinaryVersion) =>
    Seq(
      Artifact(
        BuildInfo.organization,
        BuildInfo.runtimeArtifactName + "_" + scalaBinaryVersion.prefix,
        BuildInfo.version))
}
