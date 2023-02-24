package org.apache.pekko.grpc.gradle

import com.google.protobuf.gradle.ProtobufPlugin
import org.apache.commons.lang.SystemUtils
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.scala.ScalaPlugin
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector
import org.gradle.util.GradleVersion
import org.gradle.util.VersionNumber

import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path

import static org.apache.pekko.grpc.gradle.PekkoGrpcPluginExtension.*

class PekkoGrpcPlugin implements Plugin<Project> {

    Project project

    // workaround for test projects, when one only neesd to tests a new plugin version without rebuilding dependencies.
    String getBaselineVersion(String pluginVersion) {
        def pv = System.getProperty("pekko.grpc.baseline.version", pluginVersion)
        if (VersionNumber.parse(pv).qualifier) {
            pv + "-SNAPSHOT"
        } else {
            pv
        }
    }

    @Override
    void apply(Project project) {

        if (VersionNumber.parse(GradleVersion.current().version) < VersionNumber.parse("5.6")) {
            throw new GradleException("Gradle version is ${GradleVersion.current().version}. Minimum supported version is 5.6")
        }

        this.project = project
        def pekkoGrpcExt = project.extensions.create('pekkoGrpc', PekkoGrpcPluginExtension, project)

        if (pekkoGrpcExt.scala) {
            project.pluginManager.apply ScalaPlugin
        } else {
            project.pluginManager.apply JavaPlugin
        }

        project.pluginManager.apply ProtobufPlugin

        def baselineVersion = getBaselineVersion(pekkoGrpcExt.pluginVersion)

        project.repositories {
            mavenLocal()
            mavenCentral()
            if (VersionNumber.parse(baselineVersion).qualifier) {
                maven {
                    url "https://oss.sonatype.org/content/repositories/snapshots"
                }
                maven {
                    url "https://repository.apache.org/content/groups/snapshots"
                }
            }
        }

        String assemblySuffix = SystemUtils.IS_OS_WINDOWS ? "bat" : "jar"
        String assemblyClassifier = SystemUtils.IS_OS_WINDOWS ? "bat" : "assembly"

        Path logFile = project.buildDir.toPath().resolve("pekko-grpc-gradle-plugin.log")

        project.sourceSets {
            main {
                proto {
                    srcDir 'src/main/protobuf'
                    srcDir 'src/main/proto'
                    // Play conventions:
                    srcDir 'app/protobuf'
                    srcDir 'app/proto'
                }
            }
        }

        project.sourceSets {
            main {
                if (pekkoGrpcExt.scala) {
                    scala {
                        srcDir 'build/generated/source/proto/main/pekkoGrpc'
                        srcDir 'build/generated/source/proto/main/scalapb'
                    }
                } else {
                    java {
                        srcDir 'build/generated/source/proto/main/pekkoGrpc'
                        srcDir 'build/generated/source/proto/main/java'
                    }
                }
            }
            //TODO add test sources
        }

        project.protobuf {
            protoc {
                // Get protobuf from maven central instead of
                // using the installed version:
                artifact = "com.google.protobuf:protoc:${PROTOC_VERSION}"
            }
            plugins {
                pekkoGrpc {
                    artifact = "com.lightbend.akka.grpc:pekko-grpc-codegen_${PROTOC_PLUGIN_SCALA_VERSION}:${baselineVersion}:${assemblyClassifier}@${assemblySuffix}"
                }
                if (pekkoGrpcExt.scala) {
                    scalapb {
                        artifact = "com.lightbend.akka.grpc:pekko-grpc-scalapb-protoc-plugin_${PROTOC_PLUGIN_SCALA_VERSION}:${baselineVersion}:${assemblyClassifier}@${assemblySuffix}"
                    }
                }
            }
            generateProtoTasks {
                all().each { task ->
                    if (pekkoGrpcExt.scala) {
                        task.builtins {
                            remove java
                        }
                    }

                    task.plugins {
                        pekkoGrpc {
                            option "language=${pekkoGrpcExt.scala ? "Scala" : "Java"}"
                            option "generate_client=${pekkoGrpcExt.generateClient}"
                            option "generate_server=${pekkoGrpcExt.generateServer}"
                            option "server_power_apis=${pekkoGrpcExt.serverPowerApis}"
                            option "use_play_actions=${pekkoGrpcExt.usePlayActions}"
                            option "extra_generators=${pekkoGrpcExt.extraGenerators.join(';')}"
                            option "logfile_enc=${URLEncoder.encode(logFile.toString(), "utf-8")}"
                            if (pekkoGrpcExt.includeStdTypes) {
                                option "include_std_types=true"
                            }
                            if (pekkoGrpcExt.generatePlay) {
                                option "generate_play=true"
                            }
                            if (pekkoGrpcExt.scala) {
                                option "flat_package"
                            }
                        }
                        if (pekkoGrpcExt.scala) {
                            scalapb {
                                option "flat_package"
                            }
                        }
                    }
                }
            }
        }

        project.afterEvaluate { Project p ->
            //Check exist java source before run compileJava.
            p.tasks.getByName("compileJava").onlyIf {
                !p.sourceSets.main.allJava.isEmpty()
            }

            def scalaVersion = autodetectScala()
            p.dependencies {
                implementation "com.lightbend.akka.grpc:pekko-grpc-runtime_${scalaVersion}:${baselineVersion}"
                implementation "io.grpc:grpc-stub:${GRPC_VERSION}"
            }
        }

        project.task("printProtocLogs") {
            doLast {
                if (Files.exists(logFile)) {
                    Files.lines(logFile).forEach { line ->
                        if (line.startsWith("[info]")) logger.info(line.substring(7))
                        else if (line.startsWith("[debug]")) logger.debug(line.substring(8))
                        else if (line.startsWith("[warn]")) logger.warn(line.substring(7))
                        else if (line.startsWith("[error]")) logger.error(line.substring(8))
                    }
                }
            }
        }
        project.getTasks().getByName("printProtocLogs").dependsOn("generateProto")
        project.getTasks().getByName("compileJava").dependsOn("printProtocLogs") //TODO logs for multi project builds

    }

    String autodetectScala() {
        def cfg = project.configurations.compileClasspath.copyRecursive()

        def scalaVersions = cfg.incoming.resolutionResult.allDependencies
            .findAll { it.requested.class != DefaultProjectComponentSelector }
            .findAll {
                it.requested.moduleIdentifier.name == 'scala-library'
            }
            .collect { it.requested.versionConstraint.toString() }.collect { it.split("\\.").init().join(".") }
            .findAll { it }.unique().sort()

        if (scalaVersions.size() != 1) {
            throw new GradleException("$PLUGIN_CODE requires a single major.minor version of `org.scala-lang:scala-library` in compileClasspath.\nFound $scalaVersions")
        }

        scalaVersions.first()
    }
}

