/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.sbt

import org.apache.pekko.grpc.gen.Logger
import org.apache.pekko.grpc.gen.BuildInfo
import org.apache.pekko.grpc.gen.CodeGenerator.ScalaBinaryVersion
import sbt.CrossVersion

object GeneratorBridge {
  def sandboxedGenerator(
      codeGenerator: org.apache.pekko.grpc.gen.CodeGenerator,
      scalaBinaryVersion: ScalaBinaryVersion,
      logger: Logger): protocbridge.Generator = {
    // This matches the sbt binary version (2.12)
    val codegenScalaBinaryVersion = CrossVersion.binaryScalaVersion(BuildInfo.scalaVersion)
    protocbridge.SandboxedJvmGenerator(
      codeGenerator.name,
      protocbridge.Artifact(BuildInfo.organization, s"${BuildInfo.name}_$codegenScalaBinaryVersion", BuildInfo.version),
      codeGenerator.suggestedDependencies(scalaBinaryVersion),
      new SandboxedProtocBridgeSbtPluginCodeGenerator(_, codeGenerator.getClass.getName, logger))
  }
  def plainGenerator(
      codeGenerator: org.apache.pekko.grpc.gen.CodeGenerator,
      scalaBinaryVersion: ScalaBinaryVersion,
      logger: org.apache.pekko.grpc.gen.Logger): protocbridge.Generator = {
    val adapter = new PlainProtocBridgeSbtPluginCodeGenerator(codeGenerator, scalaBinaryVersion, logger)
    protocbridge.JvmGenerator(codeGenerator.name, adapter)
  }

  /**
   * Convert a [[org.apache.pekko.grpc.gen.CodeGenerator]] into the protocbridge-required type (without sandboxing).
   */
  private class PlainProtocBridgeSbtPluginCodeGenerator(
      impl: org.apache.pekko.grpc.gen.CodeGenerator,
      scalaBinaryVersion: ScalaBinaryVersion,
      logger: org.apache.pekko.grpc.gen.Logger)
      extends protocbridge.ProtocCodeGenerator {
    override def run(request: Array[Byte]): Array[Byte] = impl.run(request, logger)
    override def suggestedDependencies: Seq[protocbridge.Artifact] = impl.suggestedDependencies(scalaBinaryVersion)
    override def toString = s"PlainProtocBridgeSbtPluginCodeGenerator($${impl.name}: $$impl)"
  }

  /**
   * Convert a [[org.apache.pekko.grpc.gen.CodeGenerator]] into the protocbridge-required type, with sandboxing.
   */
  private class SandboxedProtocBridgeSbtPluginCodeGenerator(classLoader: ClassLoader, className: String, logger: Logger)
      extends protocbridge.ProtocCodeGenerator {
    val genClass = classLoader.loadClass(className)
    val module = genClass.getField("MODULE$").get(null)
    private val reflectiveLogger: Object =
      classLoader
        .loadClass("org.apache.pekko.grpc.gen.ReflectiveLogger")
        .asInstanceOf[Class[Object]]
        .getConstructor(classOf[Object])
        .newInstance(logger)

    private val runMethods =
      module.getClass.getMethods
        .find(m => m.getName == "run" && m.getParameterTypes()(0) == classOf[Array[Byte]])
        .getOrElse(throw new RuntimeException("Could not find 'run' method that takes an Array[Byte]"))

    override def run(request: Array[Byte]): Array[Byte] =
      runMethods.invoke(module, request, reflectiveLogger).asInstanceOf[Array[Byte]]
    override def toString = s"SandboxedProtocBridgeSbtPluginCodeGenerator(${className})"
  }

}
