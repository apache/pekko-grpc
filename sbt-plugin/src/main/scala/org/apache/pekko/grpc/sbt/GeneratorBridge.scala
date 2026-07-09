/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.sbt

import java.lang.invoke.{ MethodHandles, MethodType }

import org.apache.pekko
import pekko.grpc.gen.Logger
import pekko.grpc.gen.BuildInfo
import pekko.grpc.gen.CodeGenerator.ScalaBinaryVersion
import sbt.CrossVersion

object GeneratorBridge {
  def sandboxedGenerator(
      codeGenerator: pekko.grpc.gen.CodeGenerator,
      scalaBinaryVersion: ScalaBinaryVersion,
      logger: Logger): protocbridge.Generator = {
    // This matches the sbt binary version (2.12)
    val codegenScalaBinaryVersion = CrossVersion.binaryScalaVersion(BuildInfo.scalaVersion)
    protocbridge.SandboxedJvmGenerator.forResolver(
      codeGenerator.name,
      protocbridge.Artifact(BuildInfo.organization, s"${BuildInfo.name}_$codegenScalaBinaryVersion", BuildInfo.version),
      codeGenerator.suggestedDependencies(scalaBinaryVersion),
      new SandboxedProtocBridgeSbtPluginCodeGenerator(_, codeGenerator.getClass.getName, logger))
  }
  def plainGenerator(
      codeGenerator: pekko.grpc.gen.CodeGenerator,
      scalaBinaryVersion: ScalaBinaryVersion,
      logger: pekko.grpc.gen.Logger): protocbridge.Generator = {
    val adapter = new PlainProtocBridgeSbtPluginCodeGenerator(codeGenerator, scalaBinaryVersion, logger)
    protocbridge.JvmGenerator(codeGenerator.name, adapter)
  }

  /**
   * Convert a [[pekko.grpc.gen.CodeGenerator]] into the protocbridge-required type (without sandboxing).
   */
  private class PlainProtocBridgeSbtPluginCodeGenerator(
      impl: pekko.grpc.gen.CodeGenerator,
      scalaBinaryVersion: ScalaBinaryVersion,
      logger: pekko.grpc.gen.Logger)
      extends protocbridge.ProtocCodeGenerator {
    override def run(request: Array[Byte]): Array[Byte] = impl.run(request, logger)
    override def suggestedDependencies: Seq[protocbridge.Artifact] = impl.suggestedDependencies(scalaBinaryVersion)
    override def toString = s"PlainProtocBridgeSbtPluginCodeGenerator($${impl.name}: $$impl)"
  }

  /**
   * Convert a [[pekko.grpc.gen.CodeGenerator]] into the protocbridge-required type, with sandboxing.
   */
  private class SandboxedProtocBridgeSbtPluginCodeGenerator(classLoader: ClassLoader, className: String, logger: Logger)
      extends protocbridge.ProtocCodeGenerator {
    private val lookup = MethodHandles.publicLookup()
    private val genClass = classLoader.loadClass(className)
    private val module: Object = lookup.findStaticGetter(genClass, "MODULE$", genClass).invoke()
    private val loggerClass = classLoader.loadClass("org.apache.pekko.grpc.gen.Logger")
    private val reflectiveLoggerClass = classLoader.loadClass("org.apache.pekko.grpc.gen.ReflectiveLogger")
    private val reflectiveLogger: Object =
      lookup
        .findConstructor(
          reflectiveLoggerClass,
          MethodType.methodType(Void.TYPE, classOf[Object]))
        .invoke(logger)

    private val runMethod =
      lookup.findVirtual(
        genClass,
        "run",
        MethodType.methodType(classOf[Array[Byte]], classOf[Array[Byte]], loggerClass))

    override def run(request: Array[Byte]): Array[Byte] =
      runMethod.invoke(module, request, reflectiveLogger).asInstanceOf[Array[Byte]]
    override def toString = s"SandboxedProtocBridgeSbtPluginCodeGenerator(${className})"
  }

}
