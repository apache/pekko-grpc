/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.grpc.build

import java.io.File
import java.lang.invoke.{ MethodHandles, MethodType }
import java.net.{ URL, URLClassLoader }
import sbt._
import sbt.Keys._
import sbtprotoc.ProtocPlugin
import ProtocPlugin.autoImport.PB
import protocbridge.Target
import sbt.ProjectRef
import sbt.file

import scala.collection.mutable.ListBuffer
import protocbridge.{ Artifact => BridgeArtifact }

/** A plugin that allows to use a code generator compiled in one subproject to be used in a test project */
object ReflectiveCodeGen extends AutoPlugin {
  lazy val generatedLanguages =
    SettingKey[Seq[String]]("reflectiveGrpcGeneratedLanguages", "Generated languages").withRank(KeyRanks.Invisible)
  lazy val generatedSources =
    SettingKey[Seq[String]]("reflectiveGrpcGeneratedSources", "Generated sources").withRank(KeyRanks.Invisible)
  lazy val extraGenerators =
    SettingKey[Seq[String]]("reflectiveGrpcExtraGenerators", "Extra code generators").withRank(KeyRanks.Invisible)
  lazy val codeGeneratorSettings =
    SettingKey[Seq[String]]("codeGeneratorSettings", "Code generator settings").withRank(KeyRanks.Invisible)
  lazy val protocOptions =
    SettingKey[Seq[String]]("protocOptions", "Protoc options.").withRank(KeyRanks.Invisible)

  // needed to be able to override the PB.generate task reliably
  override lazy val requires = ProtocPlugin

  override lazy val projectSettings =
    inConfig(Compile)(
      Seq(
        PB.protocOptions := protocOptions.value,
        PB.generate :=
          // almost the same as `Def.sequential` but will return the "middle" value, ie. the result of the generation
          // Defines three steps:
          //   1) dynamically load the current code generator and plug it in the mutable generator
          //   2) run the generator
          //   3) delete the generation cache because it doesn't know that the generator may change
          Def.taskDyn {
            val _ = setCodeGenerator.value
            Def.taskDyn {
              val generationResult = generateTaskFromProtocPlugin.value

              Def.task {
                // path is defined in ProtocPlugin.sourceGeneratorTask
                val file = (PB.generate / streams).value.cacheDirectory / s"protobuf_${scalaBinaryVersion.value}"
                IO.delete(file)

                generationResult
              }
            }
          }.value,
        // HACK: make the targets mutable, so we can fill them while running the above PB.generate
        PB.targets := scala.collection.mutable.ListBuffer.empty,
        // Put an artifact resolver that returns the project's classpath for our generators
        PB.artifactResolver := Def.taskDyn {
          val cp = (ProjectRef(file("."), "codegen") / Compile / fullClasspath).value.map(_.data)
          val oldResolver = PB.artifactResolver.value
          Def.task { (artifact: BridgeArtifact) =>
            artifact.groupId match {
              case "org.apache.pekko" =>
                cp
              case _ =>
                oldResolver(artifact)
            }
          }
        }.value,
        setCodeGenerator := Def.taskDyn {
          // Scala 2.12 still uses the sbt-plugin classpath. Library cross builds use codegen
          // directly so forced 2.13/3.3 matrix runs do not compile the sbt 2 plugin project.
          val generatorProject =
            if (scalaBinaryVersion.value == "2.12") "sbt-plugin"
            else "codegen"

          Def.task {
            loadAndSetGenerator(
              (ProjectRef(file("."), generatorProject) / Compile / fullClasspath).value,
              generatedLanguages.value,
              generatedSources.value,
              extraGenerators.value,
              sourceManaged.value,
              codeGeneratorSettings.value ++ {
                if (scalaBinaryVersion.value == "3") Seq("scala3_sources") else Seq.empty
              },
              PB.targets.value.asInstanceOf[ListBuffer[Target]],
              scalaBinaryVersion.value)
          }
        }.value,
        (Compile / PB.protoSources) := PB.protoSources.value ++ Seq(
          PB.externalIncludePath.value,
          sourceDirectory.value / "proto"))) ++ Seq(
      (Global / codeGeneratorSettings) := Nil,
      (Global / generatedLanguages) := Seq("Scala"),
      (Global / generatedSources) := Seq("Client", "Server"),
      (Global / extraGenerators) := Seq.empty,
      (Global / protocOptions) := Seq.empty,
      watchSources ++= (ProjectRef(file("."), "codegen") / watchSources).value,
      watchSources ++= (ProjectRef(file("."), "sbt-plugin") / watchSources).value)

  lazy val setCodeGenerator = taskKey[Unit]("grpc-set-code-generator")

  def loadAndSetGenerator(
      classpath: Classpath,
      languages0: Seq[String],
      sources0: Seq[String],
      extraGenerators0: Seq[String],
      targetPath: File,
      generatorSettings: Seq[String],
      targets: ListBuffer[Target],
      scalaBinaryVersion: String): Unit = {
    loadAndSetGeneratorWithMethodHandles(
      classpath,
      languages0,
      sources0,
      extraGenerators0,
      targetPath,
      generatorSettings,
      targets,
      scalaBinaryVersion)
  }

  private def loadAndSetGeneratorWithMethodHandles(
      classpath: Classpath,
      languages0: Seq[String],
      sources0: Seq[String],
      extraGenerators0: Seq[String],
      targetPath: File,
      generatorSettings: Seq[String],
      targets: ListBuffer[Target],
      scalaBinaryVersion: String): Unit = {
    val generatorClasspath = classpath.map(_.data)
    val codegenArtifact =
      BridgeArtifact("org.apache.pekko", s"pekko-grpc-codegen_$scalaBinaryVersion", "0.0.0")

    final case class GeneratorDefinition(name: String, className: String, suggestedDependencies: Seq[BridgeArtifact])

    def codeGenerator(name: String): GeneratorDefinition = {
      name match {
        case "ScalaTraitCodeGenerator" =>
          GeneratorDefinition(
            "pekko-grpc-scaladsl-trait",
            "org.apache.pekko.grpc.gen.scaladsl.ScalaTraitCodeGenerator$",
            Seq.empty)
        case "ScalaClientCodeGenerator" =>
          GeneratorDefinition(
            "pekko-grpc-scaladsl-client",
            "org.apache.pekko.grpc.gen.scaladsl.ScalaClientCodeGenerator$",
            Seq.empty)
        case "ScalaServerCodeGenerator" =>
          GeneratorDefinition(
            "pekko-grpc-scaladsl-server",
            "org.apache.pekko.grpc.gen.scaladsl.ScalaServerCodeGenerator$",
            Seq.empty)
        case "ScalaMarshallersCodeGenerator" =>
          GeneratorDefinition(
            "pekko-grpc-scaladsl-server-marshallers",
            "org.apache.pekko.grpc.gen.scaladsl.ScalaMarshallersCodeGenerator$",
            Seq.empty)
        case "JavaInterfaceCodeGenerator" =>
          GeneratorDefinition(
            "pekko-grpc-javadsl-interface",
            "org.apache.pekko.grpc.gen.javadsl.JavaInterfaceCodeGenerator$",
            Seq.empty)
        case "JavaClientCodeGenerator" =>
          GeneratorDefinition(
            "pekko-grpc-javadsl-client",
            "org.apache.pekko.grpc.gen.javadsl.JavaClientCodeGenerator$",
            Seq.empty)
        case "JavaServerCodeGenerator" =>
          GeneratorDefinition(
            "pekko-grpc-javadsl-server",
            "org.apache.pekko.grpc.gen.javadsl.JavaServerCodeGenerator$",
            Seq.empty)
        case _ =>
          GeneratorDefinition(name, s"org.apache.pekko.grpc.gen.scaladsl.$name$$", Seq.empty)
      }
    }

    def sandboxedGenerator(definition: GeneratorDefinition): protocbridge.Generator =
      protocbridge.SandboxedJvmGenerator.forResolver(
        definition.name,
        codegenArtifact,
        definition.suggestedDependencies,
        new MethodHandleProtocCodeGenerator(_, definition.className, generatorClasspath))

    def scalaBaseGenerators: Seq[protocbridge.Generator] =
      Seq(scalapb.gen.SandboxedGenerator, sandboxedGenerator(codeGenerator("ScalaTraitCodeGenerator")))
    def javaBaseGenerators: Seq[protocbridge.Generator] =
      Seq(PB.gens.java, sandboxedGenerator(codeGenerator("JavaInterfaceCodeGenerator")))

    val baseGenerators = languages0 match {
      case Seq("Scala") => scalaBaseGenerators
      case Seq("Java")  => javaBaseGenerators
      case Seq(_, _)    => scalaBaseGenerators ++ javaBaseGenerators
    }

    val stubGenerators = (for {
      source <- sources0
      language <- languages0
    } yield (source, language) match {
      case ("Client", "Scala") => codeGenerator("ScalaClientCodeGenerator")
      case ("Server", "Scala") => codeGenerator("ScalaServerCodeGenerator")
      case ("Client", "Java")  => codeGenerator("JavaClientCodeGenerator")
      case ("Server", "Java")  => codeGenerator("JavaServerCodeGenerator")
    }).distinct.map(sandboxedGenerator)

    val generators =
      (if (stubGenerators.nonEmpty) baseGenerators ++ stubGenerators else stubGenerators) ++
      extraGenerators0.map(codeGenerator).map(sandboxedGenerator)

    val protocJavaSettings =
      Set("single_line_to_proto_string", "ascii_format_to_string", "retain_source_code_info")
    val scalapbSettings =
      Set("java_conversions", "flat_package", "single_line_to_proto_string", "ascii_format_to_string", "no_lenses",
        "retain_source_code_info", "grpc", "scala3_sources")

    val generatedTargets = generators.map { generator =>
      val settings = generator match {
        case PB.gens.java                                                           => generatorSettings.filter(protocJavaSettings)
        case protocbridge.JvmGenerator("scala", _) | scalapb.gen.SandboxedGenerator =>
          generatorSettings.filter(scalapbSettings)
        case _ =>
          generatorSettings
      }
      Target(generator, targetPath, settings)
    }

    targets.clear()
    targets ++= generatedTargets
  }

  private final class MethodHandleProtocCodeGenerator(
      classLoader: ClassLoader,
      className: String,
      classpath: Seq[File])
      extends protocbridge.ProtocCodeGenerator {
    private val childFirstClassLoader =
      new ChildFirstClassLoader(classpath.map(_.toURI.toURL).toArray, classLoader)
    private val lookup = MethodHandles.publicLookup()
    private val moduleClass = childFirstClassLoader.loadClass(className)
    private val module: Object =
      lookup.findStaticGetter(moduleClass, "MODULE$", moduleClass).invoke()
    private val loggerClass = childFirstClassLoader.loadClass("org.apache.pekko.grpc.gen.Logger")
    private val loggerModuleClass = childFirstClassLoader.loadClass("org.apache.pekko.grpc.gen.SilencedLogger$")
    private val logger: Object =
      lookup.findStaticGetter(loggerModuleClass, "MODULE$", loggerModuleClass).invoke()
    private val runMethod = lookup.findVirtual(
      moduleClass,
      "run",
      MethodType.methodType(classOf[Array[Byte]], classOf[Array[Byte]], loggerClass))

    override def run(request: Array[Byte]): Array[Byte] =
      runMethod.invoke(module, request.asInstanceOf[Object], logger).asInstanceOf[Array[Byte]]

    override def toString = s"MethodHandleProtocCodeGenerator($className)"
  }

  private final class ChildFirstClassLoader(urls: Array[URL], parent: ClassLoader)
      extends URLClassLoader(urls, parent) {
    override def loadClass(name: String, resolve: Boolean): Class[?] =
      getClassLoadingLock(name).synchronized {
        val loaded: Class[?] = findLoadedClass(name)
        val clazz: Class[?] =
          if (loaded != null) loaded
          else if (isPlatformClass(name)) ClassLoader.getPlatformClassLoader.loadClass(name)
          else {
            try findClass(name)
            catch {
              case _: ClassNotFoundException => super.loadClass(name, false)
            }
          }
        if (resolve) resolveClass(clazz)
        clazz
      }

    private def isPlatformClass(name: String): Boolean =
      name.startsWith("java.") ||
      name.startsWith("javax.") ||
      name.startsWith("jdk.") ||
      name.startsWith("sun.")
  }

  lazy val generateTaskFromProtocPlugin: Def.Initialize[Task[Seq[File]]] =
    // lookup and return `PB.generate := ...` setting from ProtocPlugin
    ProtocPlugin.projectSettings
      .find(_.key.key == PB.generate.key)
      .get
      .init
      .asInstanceOf[Def.Initialize[Task[Seq[File]]]]
}
