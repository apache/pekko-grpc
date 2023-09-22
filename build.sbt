/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

import net.bzzt.reproduciblebuilds.ReproducibleBuildsPlugin.reproducibleBuildsCheckResolver
import org.apache.pekko.grpc.Dependencies
import org.apache.pekko.grpc.Dependencies.Versions.{ scala212, scala213 }
import org.apache.pekko.grpc.ProjectExtensions._
import org.apache.pekko.grpc.NoPublish
import org.apache.pekko.grpc.build.ReflectiveCodeGen
import com.typesafe.tools.mima.core._
import sbt.Keys.scalaVersion

ThisBuild / apacheSonatypeProjectProfile := "pekko"
sourceDistName := "apache-pekko-grpc"
sourceDistIncubating := true
ThisBuild / versionScheme := Some(VersionScheme.SemVerSpec)

commands := commands.value.filterNot { command =>
  command.nameOption.exists { name =>
    name.contains("sonatypeRelease") || name.contains("sonatypeBundleRelease")
  }
}

ThisBuild / reproducibleBuildsCheckResolver := Resolver.ApacheMavenStagingRepo

ThisBuild / resolvers += Resolver.mavenLocal

val pekkoPrefix = "pekko-grpc"
val pekkoGrpcRuntimeName = s"$pekkoPrefix-runtime"

lazy val mkBatAssemblyTask = taskKey[File]("Create a Windows bat assembly")

// gradle plugin compatibility (avoid `+` in snapshot versions)
(ThisBuild / dynverSeparator) := "-"

val pekkoGrpcCodegenId = s"$pekkoPrefix-codegen"
lazy val codegen = Project(id = "codegen", base = file("codegen"))
  .enablePlugins(SbtTwirl, BuildInfoPlugin)
  .enablePlugins(ReproducibleBuildsPlugin)
  .disablePlugins(MimaPlugin)
  .settings(Dependencies.codegen)
  .settings(resolvers += Resolver.sbtPluginRepo("releases"))
  .settings(
    name := s"$pekkoPrefix-codegen",
    mkBatAssemblyTask := {
      val file = assembly.value
      Assemblies.mkBatAssembly(file)
    },
    buildInfoKeys ++= Seq[BuildInfoKey](organization, name, version, scalaVersion, sbtVersion),
    buildInfoKeys += "runtimeArtifactName" -> pekkoGrpcRuntimeName,
    buildInfoKeys += "pekkoVersion" -> Dependencies.Versions.pekko,
    buildInfoKeys += "pekkoHttpVersion" -> Dependencies.Versions.pekkoHttp,
    buildInfoKeys += "grpcVersion" -> Dependencies.Versions.grpc,
    buildInfoKeys += "googleProtocVersion" -> Dependencies.Versions.googleProtoc,
    buildInfoKeys += "googleProtobufJavaVersion" -> Dependencies.Versions.googleProtobufJava,
    buildInfoPackage := "org.apache.pekko.grpc.gen",
    (Compile / assembly / artifact) := {
      val art = (Compile / assembly / artifact).value
      art.withClassifier(Some("assembly"))
    },
    (assembly / mainClass) := Some("org.apache.pekko.grpc.gen.Main"),
    (assembly / assemblyOption) := (assembly / assemblyOption).value.withPrependShellScript(
      Some(sbtassembly.AssemblyPlugin.defaultUniversalScript(shebang = true))),
    crossScalaVersions := Dependencies.Versions.CrossScalaForLib,
    scalaVersion := scala212,
    Compile / unmanagedSourceDirectories ++= {
      if (scalaBinaryVersion.value == "2.12") {
        Seq.empty
      } else {
        Seq(
          project.base / "src" / "main" / "scala-2.13+")
      }
    })
  .settings(addArtifact(Compile / assembly / artifact, assembly))
  .settings(addArtifact(Artifact(pekkoGrpcCodegenId, "bat", "bat", "bat"), mkBatAssemblyTask))

lazy val runtime = Project(id = "runtime", base = file("runtime"))
  .settings(Dependencies.runtime)
  .settings(VersionGenerator.settings)
  .settings(MetaInfLicenseNoticeCopy.runtimeSettings)
  .settings(
    crossScalaVersions := Dependencies.Versions.CrossScalaForLib,
    scalaVersion := Dependencies.Versions.CrossScalaForLib.head)
  .settings(
    name := pekkoGrpcRuntimeName,
    mimaFailOnNoPrevious := true,
    mimaPreviousArtifacts := Set.empty, // temporarily disable mima checks
    AutomaticModuleName.settings("pekko.grpc.runtime"),
    ReflectiveCodeGen.generatedLanguages := Seq("Scala"),
    ReflectiveCodeGen.extraGenerators := Seq("ScalaMarshallersCodeGenerator"),
    PB.protocVersion := Dependencies.Versions.googleProtoc)
  .enablePlugins(org.apache.pekko.grpc.build.ReflectiveCodeGen)
  .enablePlugins(ReproducibleBuildsPlugin)

/** This could be an independent project - or does upstream provide this already? didn't find it.. */
val pekkoGrpcProtocPluginId = s"$pekkoPrefix-scalapb-protoc-plugin"
lazy val scalapbProtocPlugin = Project(id = "scalapb-protoc-plugin", base = file("scalapb-protoc-plugin"))
  .disablePlugins(MimaPlugin)
  /** TODO we only really need to depend on scalapb */
  .dependsOn(codegen)
  .settings(
    name := s"$pekkoPrefix-scalapb-protoc-plugin",
    mkBatAssemblyTask := {
      val file = assembly.value
      Assemblies.mkBatAssembly(file)
    },
    (Compile / assembly / artifact) := {
      val art = (Compile / assembly / artifact).value
      art.withClassifier(Some("assembly"))
    },
    (assembly / mainClass) := Some("org.apache.pekko.grpc.scalapb.Main"),
    (assembly / assemblyOption) := (assembly / assemblyOption).value.withPrependShellScript(
      Some(sbtassembly.AssemblyPlugin.defaultUniversalScript(shebang = true))))
  .settings(
    crossScalaVersions := Dependencies.Versions.CrossScalaForPlugin,
    scalaVersion := Dependencies.Versions.CrossScalaForPlugin.head)
  .settings(addArtifact(Compile / assembly / artifact, assembly))
  .settings(addArtifact(Artifact(pekkoGrpcProtocPluginId, "bat", "bat", "bat"), mkBatAssemblyTask))
  .enablePlugins(ReproducibleBuildsPlugin)

lazy val mavenPlugin = Project(id = "maven-plugin", base = file("maven-plugin"))
  .enablePlugins(org.apache.pekko.grpc.SbtMavenPlugin)
  .enablePlugins(ReproducibleBuildsPlugin)
  .disablePlugins(MimaPlugin)
  .settings(Dependencies.mavenPlugin)
  .settings(
    name := s"$pekkoPrefix-maven-plugin",
    crossPaths := false,
    crossScalaVersions := Dependencies.Versions.CrossScalaForPlugin,
    scalaVersion := Dependencies.Versions.CrossScalaForPlugin.head)
  .dependsOn(codegen)

lazy val sbtPlugin = Project(id = "sbt-plugin", base = file("sbt-plugin"))
  .enablePlugins(SbtPlugin)
  .enablePlugins(ReproducibleBuildsPlugin)
  .disablePlugins(MimaPlugin)
  .settings(Dependencies.sbtPlugin)
  .settings(
    name := s"$pekkoPrefix-sbt-plugin",
    /** And for scripted tests: */
    scriptedLaunchOpts += ("-Dproject.version=" + version.value),
    scriptedLaunchOpts ++= sys.props.collect { case (k @ "sbt.ivy.home", v) => s"-D$k=$v" }.toSeq,
    scriptedDependencies := {
      val p1 = publishLocal.value
      val p2 = (codegen / publishLocal).value
      val p3 = (runtime / publishLocal).value
      val p4 = (interopTests / publishLocal).value
    },
    scriptedSbt := "1.9.6",
    scriptedBufferLog := false)
  .settings(
    crossScalaVersions := Dependencies.Versions.CrossScalaForPlugin,
    scalaVersion := Dependencies.Versions.CrossScalaForPlugin.head)
  .dependsOn(codegen)

lazy val interopTests = Project(id = "interop-tests", base = file("interop-tests"))
  .disablePlugins(MimaPlugin)
  .settings(Dependencies.interopTests)
  .settings(
    crossScalaVersions := Dependencies.Versions.CrossScalaForLib,
    scalaVersion := Dependencies.Versions.CrossScalaForLib.head)
  .pluginTestingSettings
  .settings(
    name := s"$pekkoPrefix-interop-tests",
    // All io.grpc servers want to bind to port :8080
    parallelExecution := false,
    ReflectiveCodeGen.generatedLanguages := Seq("Scala", "Java"),
    ReflectiveCodeGen.extraGenerators := Seq("ScalaMarshallersCodeGenerator"),
    ReflectiveCodeGen.codeGeneratorSettings ++= Seq("server_power_apis"),
    // grpc 1.54.2 brings in extra unnecessary proto files that cause build issues
    PB.generate / excludeFilter := new SimpleFileFilter(f => f.getAbsolutePath().contains("envoy")),
    PB.protocVersion := Dependencies.Versions.googleProtoc,
    // We need to be able to publish locally in order for sbt interopt tests to work
    // however this sbt project should not be published to an actual repository
    publishLocal / skip := false,
    Compile / doc := (Compile / doc / target).value)
  .settings(inConfig(Test)(Seq(
    reStart / mainClass := (Test / run / mainClass).value, {
      import spray.revolver.Actions._
      reStart := Def
        .inputTask {
          restartApp(
            streams.value,
            reLogTag.value,
            thisProjectRef.value,
            reForkOptions.value,
            (reStart / mainClass).value,
            (reStart / fullClasspath).value,
            reStartArgs.value,
            startArgsParser.parsed)
        }
        .dependsOn(Compile / products)
        .evaluated
    }))).enablePlugins(NoPublish)

lazy val benchmarks = Project(id = "benchmarks", base = file("benchmarks"))
  .dependsOn(runtime)
  .enablePlugins(JmhPlugin)
  .disablePlugins(MimaPlugin)
  .settings(
    name := s"$pekkoPrefix-benchmarks",
    crossScalaVersions := Dependencies.Versions.CrossScalaForLib,
    scalaVersion := Dependencies.Versions.CrossScalaForLib.head)
  .enablePlugins(NoPublish)

lazy val docs = Project(id = "docs", base = file("docs"))
// Make sure code generation is run:
  .dependsOn(pluginTesterScala)
  .dependsOn(pluginTesterJava)
  .enablePlugins(PekkoParadoxPlugin, ParadoxSitePlugin, PreprocessPlugin)
  .disablePlugins(MimaPlugin)
  .settings(
    name := s"$pekkoPrefix-docs",
    makeSite := makeSite.dependsOn(LocalRootProject / ScalaUnidoc / doc).value,
    pekkoParadoxGithub := Some("https://github.com/apache/incubator-pekko-grpc"),
    previewPath := (Paradox / siteSubdirName).value,
    Preprocess / siteSubdirName := s"api/pekko-grpc/${projectInfoVersion.value}",
    Preprocess / sourceDirectory := (LocalRootProject / ScalaUnidoc / unidoc / target).value,
    Paradox / siteSubdirName := s"docs/pekko-grpc/${projectInfoVersion.value}",
    // Make sure code generation is run before paradox:
    (Compile / paradox) := (Compile / paradox).dependsOn(Compile / compile).value,
    paradoxGroups := Map("Language" -> Seq("Java", "Scala"), "Buildtool" -> Seq("sbt", "Gradle", "Maven")),
    Compile / paradoxProperties ++= Map(
      "pekko.version" -> Dependencies.Versions.pekko,
      "pekko-http.version" -> Dependencies.Versions.pekkoHttp,
      "grpc.version" -> Dependencies.Versions.grpc,
      "project.url" -> "https://pekko.apache.org/docs/pekko-grpc/current/",
      "canonical.base_url" -> "https://pekko.apache.org/docs/pekko-grpc/current",
      "scaladoc.scala.base_url" -> s"https://www.scala-lang.org/api/current/",
      // Apache Pekko
      "extref.pekko.base_url" -> s"https://pekko.apache.org/docs/pekko/current/%s",
      "scaladoc.pekko.base_url" -> "https://pekko.apache.org/docs/pekko/current/",
      "javadoc.pekko.base_url" -> "https://pekko.apache.org/docs/pekko/current/",
      // Apache Pekko HTTP
      "extref.pekko-http.base_url" -> s"https://pekko.apache.org/docs/pekko-http/${Dependencies.Versions.pekkoHttpBinary}/%s",
      "scaladoc.pekko-http.base_url" -> s"https://pekko.apache.org/api/pekko-http/${Dependencies.Versions.pekkoHttpBinary}/",
      "javadoc.pekko-http.base_url" -> s"https://pekko.apache.org/japi/pekkp-http/${Dependencies.Versions.pekkoHttpBinary}/",
      // Apache Pekko gRPC
      "scaladoc.org.apache.pekko.grpc.base_url" -> s"/${(Preprocess / siteSubdirName).value}/",
      "javadoc.org.apache.pekko.grpc.base_url" -> "" // @apidoc links to Scaladoc
    ),
    apidocRootPackage := "org.apache.pekko",
    Compile / paradoxMarkdownToHtml / sourceGenerators += Def.taskDyn {
      val targetFile = (Compile / paradox / sourceManaged).value / "license-report.md"

      (LocalRootProject / dumpLicenseReportAggregate).map { dir =>
        IO.copy(List(dir / "pekko-grpc-root-licenses.md" -> targetFile)).toList
      }
    }.taskValue)
  .settings(
    crossScalaVersions := Dependencies.Versions.CrossScalaForLib,
    scalaVersion := Dependencies.Versions.CrossScalaForLib.head)
  .enablePlugins(NoPublish)

lazy val pluginTesterScala = Project(id = "plugin-tester-scala", base = file("plugin-tester-scala"))
  .disablePlugins(MimaPlugin)
  .settings(Dependencies.pluginTester)
  .settings(
    name := s"$pekkoPrefix-plugin-tester-scala",
    fork := true,
    crossScalaVersions := Dependencies.Versions.CrossScalaForLib,
    scalaVersion := scala212,
    ReflectiveCodeGen.codeGeneratorSettings ++= Seq("flat_package", "server_power_apis"))
  .pluginTestingSettings
  .enablePlugins(NoPublish)

lazy val pluginTesterJava = Project(id = "plugin-tester-java", base = file("plugin-tester-java"))
  .disablePlugins(MimaPlugin)
  .settings(Dependencies.pluginTester)
  .settings(
    name := s"$pekkoPrefix-plugin-tester-java",
    fork := true,
    ReflectiveCodeGen.generatedLanguages := Seq("Java"),
    crossScalaVersions := Dependencies.Versions.CrossScalaForLib,
    scalaVersion := scala212,
    ReflectiveCodeGen.codeGeneratorSettings ++= Seq("server_power_apis"))
  .pluginTestingSettings
  .enablePlugins(NoPublish)

lazy val root = Project(id = "pekko-grpc", base = file("."))
  .enablePlugins(ScalaUnidocPlugin)
  .disablePlugins(SitePlugin, MimaPlugin)
  .aggregate(
    runtime,
    codegen,
    mavenPlugin,
    sbtPlugin,
    scalapbProtocPlugin,
    interopTests,
    pluginTesterScala,
    pluginTesterJava,
    benchmarks,
    docs)
  .settings(
    name := s"$pekkoPrefix-root",
    (Compile / headerCreate / unmanagedSources) := (baseDirectory.value / "project").**("*.scala").get,
    // unidoc combines sources and jars from all subprojects and that
    // might include some incompatible ones. Depending on the
    // classpath order that might lead to scaladoc compilation errors.
    // the scalapb compilerplugin has a scalapb/package$.class that conflicts
    // with the one from the scalapb runtime, so for that reason we don't produce
    // unidoc for the codegen projects:
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(runtime),
    // https://github.com/sbt/sbt/issues/3465
    // Libs and plugins must share a version. The root project must use that
    // version (and set the crossScalaVersions as empty list) so each sub-project
    // can then decide which scalaVersion and crossCalaVersions they use.
    crossScalaVersions := Nil,
    scalaVersion := scala212)
  .enablePlugins(NoPublish)
