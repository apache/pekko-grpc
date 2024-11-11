/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.grpc

import sbt._
import sbt.Keys._
import buildinfo.BuildInfo

object Dependencies {
  object Versions {
    // Update the .github workflows when these scala versions change
    val scala212 = "2.12.20"
    val scala213 = "2.13.15"
    val scala3 = "3.3.4"

    // the order in the list is important because the head will be considered the default.
    val CrossScalaForLib = Seq(scala212, scala213, scala3)
    val CrossScalaForPlugin = Seq(scala212)

    // We don't force Pekko updates because downstream projects can upgrade
    // themselves. For more information see
    // https://pekko.apache.org/docs/pekko/current/project/downstream-upgrade-strategy.html
    val pekko = PekkoCoreDependency.version
    val pekkoBinary = pekko.take(3)
    val pekkoHttp = PekkoHttpDependency.version
    val pekkoHttpBinary = pekkoHttp.take(3)

    val grpc = "1.67.1" // checked synced by VersionSyncCheckPlugin
    // Even referenced explicitly in the sbt-plugin's sbt-tests
    // If changing this, remember to update protoc plugin version to align in
    // maven-plugin/src/main/maven/plugin.xml and org.apache.pekko.grpc.sbt.PekkoGrpcPlugin
    val googleProtobufJava = "4.28.2"
    val googleProtoc = googleProtobufJava // checked synced by VersionSyncCheckPlugin

    val scalaTest = "3.2.19"

    val maven = "3.9.9"
  }

  object Compile {
    val scalapbCompilerPlugin = "com.thesamet.scalapb" %% "compilerplugin" % scalapb.compiler.Version.scalapbVersion
    val scalapbRuntime = ("com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion)
      .exclude("io.grpc", "grpc-netty")

    val grpcCore = "io.grpc" % "grpc-core" % Versions.grpc
    val grpcProtobuf = "io.grpc" % "grpc-protobuf" % Versions.grpc
    val grpcNettyShaded = "io.grpc" % "grpc-netty-shaded" % Versions.grpc
    val grpcStub = "io.grpc" % "grpc-stub" % Versions.grpc

    // Excluding grpc-alts works around a complex resolution bug
    // Details are in https://github.com/akka/akka-grpc/pull/469
    val grpcInteropTesting = ("io.grpc" % "grpc-interop-testing" % Versions.grpc)
      .exclude("io.grpc", "grpc-alts")
      .exclude("io.grpc", "grpc-xds")

    val slf4jApi = "org.slf4j" % "slf4j-api" % "2.0.16"
    val mavenPluginApi = "org.apache.maven" % "maven-plugin-api" % Versions.maven
    val mavenCore = "org.apache.maven" % "maven-core" % Versions.maven
    val protocJar = "com.github.os72" % "protoc-jar" % "3.11.4"

    val plexusBuildApi = "org.sonatype.plexus" % "plexus-build-api" % "0.0.7" % "optional"
  }

  object Test {
    final val Test = sbt.Test
    val scalaTest = "org.scalatest" %% "scalatest" % Versions.scalaTest % Test
    val scalaTestPlusJunit = "org.scalatestplus" %% "junit-4-13" % (Versions.scalaTest + ".0") % Test
  }

  object Runtime {
    val logback = "ch.qos.logback" % "logback-classic" % "1.3.14" % "runtime"
  }

  object Protobuf {
    val protobufJava = "com.google.protobuf" % "protobuf-java" % Versions.googleProtobufJava
    val googleCommonProtos = "com.google.protobuf" % "protobuf-java" % Versions.googleProtobufJava % "protobuf"
  }

  object Plugins {
    val sbtProtoc = "com.thesamet" % "sbt-protoc" % BuildInfo.sbtProtocVersion
  }

  private lazy val l = libraryDependencies

  lazy val codegen = l ++= Seq(
    Compile.scalapbCompilerPlugin,
    Protobuf.protobufJava, // or else scalapb pulls older version in transitively
    Compile.grpcProtobuf,
    Test.scalaTest)

  lazy val runtime = l ++= Seq(
    Compile.scalapbRuntime,
    Protobuf.protobufJava, // or else scalapb pulls older version in transitively
    Compile.grpcProtobuf,
    Compile.grpcCore,
    Compile.grpcStub % Provided, // comes from the generators
    Compile.grpcNettyShaded,
    Test.scalaTest,
    Test.scalaTestPlusJunit)

  lazy val mavenPlugin = l ++= Seq(
    Compile.slf4jApi,
    Compile.mavenPluginApi,
    Compile.mavenCore,
    Compile.protocJar,
    Compile.plexusBuildApi,
    Test.scalaTest)

  lazy val sbtPlugin = Seq(
    l += Compile.scalapbCompilerPlugin,
    // we depend on it in the settings of the plugin since we set keys of the sbt-protoc plugin
    addSbtPlugin(Plugins.sbtProtoc))

  lazy val interopTests = l ++= Seq(
    Compile.grpcInteropTesting,
    Compile.grpcInteropTesting % "protobuf", // gets the proto files for interop tests
    Runtime.logback,
    Test.scalaTest.withConfigurations(Some("compile")),
    Test.scalaTestPlusJunit.withConfigurations(Some("compile")))

  lazy val pluginTester = l ++= Seq(
    // usually automatically added by `suggestedDependencies`, which doesn't work with ReflectiveCodeGen
    Compile.grpcStub,
    Test.scalaTest,
    Test.scalaTestPlusJunit,
    Protobuf.protobufJava,
    Protobuf.googleCommonProtos)
}
