/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.sbt

import org.apache.pekko.grpc.gen.CodeGenerator.ScalaBinaryVersion
import org.apache.pekko.grpc.gen.scaladsl.{
  ScalaClientCodeGenerator,
  ScalaServerCodeGenerator,
  ScalaTraitCodeGenerator
}
import org.apache.pekko.grpc.gen.javadsl.{
  JavaClientCodeGenerator,
  JavaInterfaceCodeGenerator,
  JavaServerCodeGenerator
}
import org.apache.pekko.grpc.gen.{ BuildInfo, Logger => GenLogger, ProtocSettings }
import protocbridge.Generator
import sbt.Keys._
import sbt._
import sbtprotoc.ProtocPlugin
import scalapb.ScalaPbCodeGenerator

import language.implicitConversions

object PekkoGrpcPlugin extends AutoPlugin {
  import sbtprotoc.ProtocPlugin.autoImport._

  // don't enable automatically, you might not want to run it on every subproject automatically
  override def trigger = noTrigger
  override def requires = ProtocPlugin

  // hack because we cannot access sbt logger from streams unless inside taskKeys and
  // we need it in settingsKeys
  private val generatorLogger = new GenLogger {
    @volatile var logger: Logger = ConsoleLogger()
    def debug(text: String): Unit = logger.debug(text)
    def info(text: String): Unit = logger.info(text)
    def warn(text: String): Unit = logger.warn(text)
    def error(text: String): Unit = logger.error(text)
  }

  object GeneratorOption extends Enumeration {
    protected case class Val(setting: String) extends super.Val
    implicit def valueToGeneratorOptionVal(x: Value): Val = x.asInstanceOf[Val]

    val ServerPowerApis = Val("server_power_apis")
    val UsePlayActions = Val("use_play_actions")

    val settings: Set[String] = values.map(_.setting)
  }

  trait Keys { _: autoImport.type =>

    object PekkoGrpc {
      sealed trait GeneratedSource
      sealed trait GeneratedServer extends GeneratedSource
      sealed trait GeneratedClient extends GeneratedSource

      case object Client extends GeneratedClient
      case object Server extends GeneratedServer

      sealed trait Language
      case object Scala extends Language
      case object Java extends Language
    }

    val pekkoGrpcGeneratedLanguages = settingKey[Seq[PekkoGrpc.Language]](
      "Which languages to generate service and model classes for (PekkoGrpc.Scala, PekkoGrpc.Java)")
    val pekkoGrpcGeneratedSources = settingKey[Seq[PekkoGrpc.GeneratedSource]](
      "Which of the sources to generate in addition to the gRPC protobuf messages (PekkoGrpc.Server, PekkoGrpc.Client)")
    val pekkoGrpcExtraGenerators =
      settingKey[Seq[org.apache.pekko.grpc.gen.CodeGenerator]]("Extra generators to evaluate. Empty by default")
    val pekkoGrpcGenerators = settingKey[Seq[protocbridge.Generator]](
      "Generators to evaluate. Populated based on pekkoGrpcGeneratedLanguages, pekkoGrpcGeneratedSources and pekkoGrpcExtraGenerators, but can be extended if needed")
    val pekkoGrpcCodeGeneratorSettings = settingKey[Seq[String]](
      "Boolean settings to pass to the code generators, empty (all false) by default.\n" +
      "ScalaPB settings: " + ProtocSettings.scalapb.mkString(", ") + "\n" +
      "Java settings: " + ProtocSettings.protocJava.mkString(", ") + "\n" +
      "Apache Pekko gRPC settings: " + GeneratorOption.settings.mkString(", "))
  }

  object autoImport extends Keys
  import autoImport._

  override def projectSettings: Seq[sbt.Setting[_]] = defaultSettings ++ configSettings(Compile) ++ configSettings(Test)

  private def defaultSettings =
    Seq(
      pekkoGrpcCodeGeneratorSettings := Seq("flat_package"),
      pekkoGrpcGeneratedSources := Seq(PekkoGrpc.Client, PekkoGrpc.Server),
      pekkoGrpcGeneratedLanguages := Seq(PekkoGrpc.Scala),
      pekkoGrpcExtraGenerators := Seq.empty,
      libraryDependencies ++= {
        if (pekkoGrpcGeneratedLanguages.value.contains(PekkoGrpc.Scala))
          // Make scalapb.proto available for import in user-defined protos for file and package-level options
          Seq("com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf")
        else Seq()
      },
      Compile / PB.recompile := {
        // hack to get our (dirty) hands on a proper sbt logger before running the generators
        generatorLogger.logger = streams.value.log
        (Compile / PB.recompile).value
      },
      Test / PB.recompile := {
        // hack to get our (dirty) hands on a proper sbt logger before running the generators
        generatorLogger.logger = streams.value.log
        (Test / PB.recompile).value
      },
      PB.protocVersion := BuildInfo.googleProtobufVersion)

  def configSettings(config: Configuration): Seq[Setting[_]] =
    inConfig(config)(
      (if (config == Compile || config == Test) Seq() // already supported by sbt-protoc by default
       else sbtprotoc.ProtocPlugin.protobufConfigSettings) ++
      Seq(
        pekkoGrpcCodeGeneratorSettings / target := crossTarget.value / "pekko-grpc" / Defaults.nameForSrc(
          configuration.value.name),
        managedSourceDirectories += (pekkoGrpcCodeGeneratorSettings / target).value,
        unmanagedResourceDirectories ++= (PB.recompile / resourceDirectories).value,
        Defaults.ConfigGlobal / watchSources ++= (PB.recompile / sources).value,
        pekkoGrpcGenerators := {
          generatorsFor(
            pekkoGrpcGeneratedSources.value,
            pekkoGrpcGeneratedLanguages.value,
            generatorScalaBinaryVersion.value,
            generatorLogger) ++ pekkoGrpcExtraGenerators.value.map(g =>
            GeneratorBridge.plainGenerator(g, generatorScalaBinaryVersion.value, generatorLogger))
        },
        // configure the proto gen automatically by adding our codegen:
        PB.targets ++=
          targetsFor(
            (pekkoGrpcCodeGeneratorSettings / target).value,
            pekkoGrpcCodeGeneratorSettings.value,
            pekkoGrpcGenerators.value),
        PB.protoSources += sourceDirectory.value / "proto") ++
      inTask(PB.recompile)(Seq(
        includeFilter := GlobFilter("*.proto"),
        managedSourceDirectories := Nil,
        unmanagedSourceDirectories := Seq(sourceDirectory.value),
        sourceDirectories := unmanagedSourceDirectories.value ++ managedSourceDirectories.value,
        managedSources := Nil,
        unmanagedSources := { Defaults.collectFiles(unmanagedSourceDirectories, includeFilter, excludeFilter).value },
        sources := managedSources.value ++ unmanagedSources.value,
        managedResourceDirectories := Nil,
        unmanagedResourceDirectories := resourceDirectory.value +: PB.protoSources.value,
        resourceDirectories := unmanagedResourceDirectories.value ++ managedResourceDirectories.value,
        managedResources := Nil,
        unmanagedResources := {
          Defaults.collectFiles(unmanagedResourceDirectories, includeFilter, excludeFilter).value
        },
        resources := managedResources.value ++ unmanagedResources.value)))

  def targetsFor(
      targetPath: File,
      settings: Seq[String],
      generators: Seq[protocbridge.Generator]): Seq[protocbridge.Target] =
    generators.map { generator =>
      protocbridge.Target(
        generator,
        targetPath,
        generator match {
          case PB.gens.java =>
            settings.filter(ProtocSettings.protocJava.contains)
          case protocbridge.JvmGenerator("scala", ScalaPbCodeGenerator) | scalapb.gen.SandboxedGenerator =>
            settings.filter(ProtocSettings.scalapb.contains)
          case _ =>
            settings
        })
    }

  // creates a seq of generator and per generator settings
  def generatorsFor(
      stubs: Seq[PekkoGrpc.GeneratedSource],
      languages: Seq[PekkoGrpc.Language],
      scalaBinaryVersion: ScalaBinaryVersion,
      logger: GenLogger): Seq[protocbridge.Generator] = {
    import PekkoGrpc._
    def toGen(codeGenerator: org.apache.pekko.grpc.gen.CodeGenerator) =
      GeneratorBridge.sandboxedGenerator(codeGenerator, scalaBinaryVersion, logger)
    // these two are the model/message (protoc) generators
    def ScalaGenerator: protocbridge.Generator = scalapb.gen.SandboxedGenerator
    // we have a default flat_package, but that doesn't play with the java generator (it fails)
    def JavaGenerator: protocbridge.Generator = PB.gens.java

    lazy val scalaBaseGenerators: Seq[Generator] = Seq(ScalaGenerator, toGen(ScalaTraitCodeGenerator))
    lazy val javaBaseGenerators: Seq[Generator] = Seq(JavaGenerator, toGen(JavaInterfaceCodeGenerator))
    lazy val baseGenerators: Seq[Generator] = languages match {
      case Seq(Scala) => scalaBaseGenerators
      case Seq(Java)  => javaBaseGenerators
      case Seq(_, _)  => scalaBaseGenerators ++ javaBaseGenerators
    }

    val generators = (for {
      stub <- stubs
      language <- languages
    } yield (stub, language) match {
      case (Client, Scala) => ScalaClientCodeGenerator
      case (Server, Scala) => ScalaServerCodeGenerator
      case (Client, Java)  => JavaClientCodeGenerator
      case (Server, Java)  => JavaServerCodeGenerator
    }).distinct.map(toGen)

    if (generators.nonEmpty) baseGenerators ++ generators
    else generators
  }

  // workaround to allow using Scala 2.13 artifacts in Scala 3 projects
  def generatorScalaBinaryVersion: Def.Initialize[ScalaBinaryVersion] = Def.setting {
    ScalaBinaryVersion {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, _)) => "2.13"
        case _            => scalaBinaryVersion.value
      }
    }
  }

  /** Sandbox a CodeGenerator, to prepare it to be added to pekkoGrpcGenerators */
  def sandboxedGenerator(
      codeGenerator: org.apache.pekko.grpc.gen.CodeGenerator): Def.Initialize[protocbridge.Generator] =
    Def.setting {
      GeneratorBridge.sandboxedGenerator(codeGenerator, generatorScalaBinaryVersion.value, generatorLogger)
    }

  /** Convert a CodeGenerator, to prepare it to be added to pekkoGrpcGenerators without sandboxing */
  def plainGenerator(codeGenerator: org.apache.pekko.grpc.gen.CodeGenerator): Def.Initialize[protocbridge.Generator] =
    Def.setting {
      GeneratorBridge.plainGenerator(codeGenerator, generatorScalaBinaryVersion.value, generatorLogger)
    }
}
