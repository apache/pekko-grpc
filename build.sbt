import org.apache.pekko.grpc.Dependencies
import org.apache.pekko.grpc.Dependencies.Versions.{ scala212, scala213 }
import org.apache.pekko.grpc.ProjectExtensions._
import org.apache.pekko.grpc.build.ReflectiveCodeGen
import com.typesafe.tools.mima.core._
import sbt.Keys.scalaVersion

val pekkoGrpcRuntimeName = "pekko-grpc-runtime"

lazy val mkBatAssemblyTask = taskKey[File]("Create a Windows bat assembly")

// gradle plugin compatibility (avoid `+` in snapshot versions)
(ThisBuild / dynverSeparator) := "-"

ThisBuild / resolvers += "Apache Snapshots".at("https://repository.apache.org/content/repositories/snapshots/")

val pekkoGrpcCodegenId = "pekko-grpc-codegen"
lazy val codegen = Project(id = pekkoGrpcCodegenId, base = file("codegen"))
  .enablePlugins(SbtTwirl, BuildInfoPlugin)
  .enablePlugins(ReproducibleBuildsPlugin)
  .disablePlugins(MimaPlugin)
  .settings(Dependencies.codegen)
  .settings(resolvers += Resolver.sbtPluginRepo("releases"))
  .settings(
    mkBatAssemblyTask := {
      val file = assembly.value
      Assemblies.mkBatAssembly(file)
    },
    buildInfoKeys ++= Seq[BuildInfoKey](organization, name, version, scalaVersion, sbtVersion),
    buildInfoKeys += "runtimeArtifactName" -> pekkoGrpcRuntimeName,
    buildInfoKeys += "pekkoVersion" -> Dependencies.Versions.pekko,
    buildInfoKeys += "pekkoHttpVersion" -> Dependencies.Versions.pekkoHttp,
    buildInfoKeys += "grpcVersion" -> Dependencies.Versions.grpc,
    buildInfoKeys += "googleProtobufVersion" -> Dependencies.Versions.googleProtobuf,
    buildInfoPackage := "org.apache.pekko.grpc.gen",
    (Compile / assembly / artifact) := {
      val art = (Compile / assembly / artifact).value
      art.withClassifier(Some("assembly"))
    },
    (assembly / mainClass) := Some("org.apache.pekko.grpc.gen.Main"),
    (assembly / assemblyOption) := (assembly / assemblyOption).value.withPrependShellScript(
      Some(sbtassembly.AssemblyPlugin.defaultUniversalScript(shebang = true))),
    crossScalaVersions := Dependencies.Versions.CrossScalaForPlugin,
    scalaVersion := scala212)
  .settings(addArtifact(Compile / assembly / artifact, assembly))
  .settings(addArtifact(Artifact(pekkoGrpcCodegenId, "bat", "bat", "bat"), mkBatAssemblyTask))

lazy val runtime = Project(id = pekkoGrpcRuntimeName, base = file("runtime"))
  .settings(Dependencies.runtime)
  .settings(VersionGenerator.settings)
  .settings(
    crossScalaVersions := Dependencies.Versions.CrossScalaForLib,
    scalaVersion := Dependencies.Versions.CrossScalaForLib.head)
  .settings(
    mimaFailOnNoPrevious := true,
    mimaPreviousArtifacts := Set.empty, // temporarily disable mima checks
    AutomaticModuleName.settings("pekko.grpc.runtime"),
    MetaInfLicenseNoticeCopy.settings,
    ReflectiveCodeGen.generatedLanguages := Seq("Scala"),
    ReflectiveCodeGen.extraGenerators := Seq("ScalaMarshallersCodeGenerator"),
    PB.protocVersion := Dependencies.Versions.googleProtobuf)
  .enablePlugins(org.apache.pekko.grpc.build.ReflectiveCodeGen)
  .enablePlugins(ReproducibleBuildsPlugin)

/** This could be an independent project - or does upstream provide this already? didn't find it.. */
val pekkoGrpcProtocPluginId = "pekko-grpc-scalapb-protoc-plugin"
lazy val scalapbProtocPlugin = Project(id = pekkoGrpcProtocPluginId, base = file("scalapb-protoc-plugin"))
  .disablePlugins(MimaPlugin)
  /** TODO we only really need to depend on scalapb */
  .dependsOn(codegen)
  .settings(
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
  .settings(MetaInfLicenseNoticeCopy.settings)
  .settings(addArtifact(Compile / assembly / artifact, assembly))
  .settings(addArtifact(Artifact(pekkoGrpcProtocPluginId, "bat", "bat", "bat"), mkBatAssemblyTask))
  .enablePlugins(ReproducibleBuildsPlugin)

lazy val mavenPlugin = Project(id = "pekko-grpc-maven-plugin", base = file("maven-plugin"))
  .enablePlugins(org.apache.pekko.grpc.SbtMavenPlugin)
  .enablePlugins(ReproducibleBuildsPlugin)
  .disablePlugins(MimaPlugin)
  .settings(Dependencies.mavenPlugin)
  .settings(
    publishMavenStyle := true,
    crossPaths := false,
    crossScalaVersions := Dependencies.Versions.CrossScalaForPlugin,
    scalaVersion := Dependencies.Versions.CrossScalaForPlugin.head)
  .settings(MetaInfLicenseNoticeCopy.settings)
  .dependsOn(codegen)

lazy val sbtPlugin = Project(id = "sbt-pekko-grpc", base = file("sbt-plugin"))
  .enablePlugins(SbtPlugin)
  .enablePlugins(ReproducibleBuildsPlugin)
  .disablePlugins(MimaPlugin)
  .settings(Dependencies.sbtPlugin)
  .settings(
    /** And for scripted tests: */
    scriptedLaunchOpts += ("-Dproject.version=" + version.value),
    scriptedLaunchOpts ++= sys.props.collect { case (k @ "sbt.ivy.home", v) => s"-D$k=$v" }.toSeq,
    scriptedDependencies := {
      val p1 = publishLocal.value
      val p2 = (codegen / publishLocal).value
      val p3 = (runtime / publishLocal).value
      val p4 = (interopTests / publishLocal).value
    },
    scriptedBufferLog := false)
  .settings(
    crossScalaVersions := Dependencies.Versions.CrossScalaForPlugin,
    scalaVersion := Dependencies.Versions.CrossScalaForPlugin.head)
  .settings(MetaInfLicenseNoticeCopy.settings)
  .dependsOn(codegen)

lazy val interopTests = Project(id = "pekko-grpc-interop-tests", base = file("interop-tests"))
  .disablePlugins(MimaPlugin)
  .settings(Dependencies.interopTests)
  .settings(
    crossScalaVersions := Dependencies.Versions.CrossScalaForLib,
    scalaVersion := Dependencies.Versions.CrossScalaForLib.head)
  .pluginTestingSettings
  .settings(
    // All io.grpc servers want to bind to port :8080
    parallelExecution := false,
    ReflectiveCodeGen.generatedLanguages := Seq("Scala", "Java"),
    ReflectiveCodeGen.extraGenerators := Seq("ScalaMarshallersCodeGenerator"),
    ReflectiveCodeGen.codeGeneratorSettings ++= Seq("server_power_apis"),
    PB.protocVersion := Dependencies.Versions.googleProtobuf,
    // This project should use 'publish/skip := true', but we need
    // to be able to `publishLocal` to run the interop tests as an
    // sbt scripted test. At least skip scaladoc generation though.
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
    })))
  .settings(MetaInfLicenseNoticeCopy.settings)

lazy val benchmarks = Project(id = "benchmarks", base = file("benchmarks"))
  .dependsOn(runtime)
  .enablePlugins(JmhPlugin)
  .disablePlugins(MimaPlugin)
  .settings(
    crossScalaVersions := Dependencies.Versions.CrossScalaForLib,
    scalaVersion := Dependencies.Versions.CrossScalaForLib.head,
    (publish / skip) := true)
  .settings(MetaInfLicenseNoticeCopy.settings)

lazy val docs = Project(id = "pekko-grpc-docs", base = file("docs"))
// Make sure code generation is run:
  .dependsOn(pluginTesterScala)
  .dependsOn(pluginTesterJava)
  .enablePlugins(AkkaParadoxPlugin, ParadoxSitePlugin, PreprocessPlugin, PublishRsyncPlugin)
  .disablePlugins(MimaPlugin)
  .settings(
    name := "Apache Pekko gRPC",
    publish / skip := true,
    makeSite := makeSite.dependsOn(LocalRootProject / ScalaUnidoc / doc).value,
    previewPath := (Paradox / siteSubdirName).value,
    Preprocess / siteSubdirName := s"api/akka-grpc/${projectInfoVersion.value}",
    Preprocess / sourceDirectory := (LocalRootProject / ScalaUnidoc / unidoc / target).value,
    Paradox / siteSubdirName := s"docs/akka-grpc/${projectInfoVersion.value}",
    // Make sure code generation is run before paradox:
    (Compile / paradox) := (Compile / paradox).dependsOn(Compile / compile).value,
    paradoxGroups := Map("Language" -> Seq("Java", "Scala"), "Buildtool" -> Seq("sbt", "Gradle", "Maven")),
    Compile / paradoxProperties ++= Map(
      "pekko.version" -> Dependencies.Versions.pekko,
      "pekko-http.version" -> Dependencies.Versions.pekkoHttp,
      "grpc.version" -> Dependencies.Versions.grpc,
      "project.url" -> "https://doc.akka.io/docs/akka-grpc/current/",
      "canonical.base_url" -> "https://doc.akka.io/docs/akka-grpc/current",
      "scaladoc.scala.base_url" -> s"https://www.scala-lang.org/api/current/",
      // Akka
      "extref.akka.base_url" -> s"https://doc.akka.io/docs/akka/${Dependencies.Versions.akkaBinary}/%s",
      "scaladoc.akka.base_url" -> s"https://doc.akka.io/api/akka/${Dependencies.Versions.akkaBinary}",
      "javadoc.akka.base_url" -> s"https://doc.akka.io/japi/akka/${Dependencies.Versions.akkaBinary}/",
      // Akka HTTP
      "extref.akka-http.base_url" -> s"https://doc.akka.io/docs/akka-http/${Dependencies.Versions.akkaHttpBinary}/%s",
      "scaladoc.akka.http.base_url" -> s"https://doc.akka.io/api/akka-http/${Dependencies.Versions.akkaHttpBinary}/",
      "javadoc.akka.http.base_url" -> s"https://doc.akka.io/japi/akka-http/${Dependencies.Versions.akkaHttpBinary}/",
      // Apache Pekko gRPC
      "scaladoc.akka.grpc.base_url" -> s"/${(Preprocess / siteSubdirName).value}/",
      "javadoc.akka.grpc.base_url" -> "" // @apidoc links to Scaladoc
    ),
    apidocRootPackage := "akka",
    resolvers += Resolver.jcenterRepo,
    publishRsyncArtifacts += makeSite.value -> "www/",
    publishRsyncHost := "akkarepo@gustav.akka.io")
  .settings(
    crossScalaVersions := Dependencies.Versions.CrossScalaForLib,
    scalaVersion := Dependencies.Versions.CrossScalaForLib.head)

lazy val pluginTesterScala = Project(id = "pekko-grpc-plugin-tester-scala", base = file("plugin-tester-scala"))
  .disablePlugins(MimaPlugin)
  .settings(Dependencies.pluginTester)
  .settings(
    (publish / skip) := true,
    fork := true,
    crossScalaVersions := Dependencies.Versions.CrossScalaForLib,
    scalaVersion := scala212,
    ReflectiveCodeGen.codeGeneratorSettings ++= Seq("flat_package", "server_power_apis"))
  .settings(MetaInfLicenseNoticeCopy.settings)
  .pluginTestingSettings

lazy val pluginTesterJava = Project(id = "pekko-grpc-plugin-tester-java", base = file("plugin-tester-java"))
  .disablePlugins(MimaPlugin)
  .settings(Dependencies.pluginTester)
  .settings(
    (publish / skip) := true,
    fork := true,
    ReflectiveCodeGen.generatedLanguages := Seq("Java"),
    crossScalaVersions := Dependencies.Versions.CrossScalaForLib,
    scalaVersion := scala212,
    ReflectiveCodeGen.codeGeneratorSettings ++= Seq("server_power_apis"))
  .settings(MetaInfLicenseNoticeCopy.settings)
  .pluginTestingSettings

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
    (publish / skip) := true,
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
