/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

package org.apache.pekko.grpc

import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin
import org.apache.pekko.grpc.Dependencies.Versions.{ scala212, scala213, scala3 }
import com.lightbend.paradox.projectinfo.ParadoxProjectInfoPluginKeys.projectInfoVersion
import com.typesafe.tools.mima.plugin.MimaKeys._
import org.mdedetrich.apache.sonatype.ApacheSonatypePlugin
import sbtdynver.DynVerPlugin
import sbtdynver.DynVerPlugin.autoImport.dynverSonatypeSnapshots

object Common extends AutoPlugin {
  override def trigger = allRequirements

  override def requires = JvmPlugin && ApacheSonatypePlugin && DynVerPlugin

  private val consoleDisabledOptions = Seq("-Xfatal-warnings", "-Ywarn-unused", "-Ywarn-unused-import")

  val isScala3 = Def.setting(scalaBinaryVersion.value == "3")

  override def globalSettings =
    Seq(
      resolvers ++= Resolver.sonatypeOssRepos("staging"),
      resolvers += "Apache Nexus Snapshots".at("https://repository.apache.org/content/repositories/snapshots/"),
      homepage := Some(url("https://pekko.apache.org//")),
      scmInfo := Some(ScmInfo(url("https://github.com/apache/incubator-pekko-grpc"),
        "git@github.com:apache/incubator-pekko-grpc")),
      developers += Developer(
        "contributors",
        "Contributors",
        "dev@pekko.apache.org",
        url("https://github.com/apache/incubator-pekko-grpc/graphs/contributors")),
      description := "Apache Pekko gRPC - Support for building streaming gRPC servers and clients on top of Pekko Streams.")

  override lazy val projectSettings = Seq(
    projectInfoVersion := (if (isSnapshot.value) "snapshot" else version.value),
    scalacOptions ++= (if (!isScala3.value)
                         Seq(
                           "-unchecked",
                           "-deprecation",
                           "-language:_",
                           "-Xfatal-warnings",
                           "-Ywarn-unused",
                           "-encoding",
                           "UTF-8")
                       else
                         Seq(
                           "-unchecked",
                           "-deprecation",
                           "-language:_",
                           "-Xfatal-warnings",
                           "-Wunused:imports",
                           "-encoding",
                           "UTF-8")),
    Compile / scalacOptions ++= (if (!isScala3.value)
                                   Seq(
                                     // Generated code for methods/fields marked 'deprecated'
                                     "-Wconf:msg=Marked as deprecated in proto file:silent",
                                     // deprecated in 2.13, but used as long as we support 2.12
                                     "-Wconf:msg=Use `scala.jdk.CollectionConverters` instead:silent",
                                     "-Wconf:msg=Use LazyList instead of Stream:silent",
                                     // ignore imports in templates (FIXME why is that trailing .* needed?)
                                     "-Wconf:src=.*.txt.*:silent")
                                 else
                                   Seq(
                                     // Generated code for methods/fields marked 'deprecated'
                                     "-Wconf:msg=Marked as deprecated in proto file:silent",
                                     // deprecated in 2.13, but used as long as we support 2.12
                                     "-Wconf:msg=Use `scala.jdk.CollectionConverters` instead:silent",
                                     "-Wconf:msg=instead of Stream:silent",
                                     "-Wconf:msg=unused import:silent",
                                     "-Wconf:cat=feature:silent")),
    Compile / console / scalacOptions ~= (_.filterNot(consoleDisabledOptions.contains)),
    javacOptions ++= List("-Xlint:unchecked", "-Xlint:deprecation"),
    Compile / doc / scalacOptions := scalacOptions.value ++ Seq(
      "-doc-title",
      "Apache Pekko gRPC",
      "-doc-version",
      version.value,
      "-sourcepath",
      (ThisBuild / baseDirectory).value.toString,
      "-doc-source-url", {
        val branch = if (isSnapshot.value) "main" else s"v${version.value}"
        s"https://github.com/apache/incubator-pekko-grpc/tree/${branch}€{FILE_PATH_EXT}#L€{FILE_LINE}"
      },
      "-doc-canonical-base-url",
      "https://pekko.apache.org/api/pekko-grpc/current/") ++ (
      if (!isScala3.value) {
        Seq(
          "-skip-packages",
          "org.apache.pekko.pattern:" + // for some reason Scaladoc creates this
          "templates")
      } else {
        Seq(
          "-skip-packages:org.apache.pekko.pattern:" + // for some reason Scaladoc creates this
          "templates")
      }),
    Compile / doc / scalacOptions -= "-Xfatal-warnings",
    apiURL := Some(
      url(s"https://pekko.apache.org/api/pekko-grpc/${projectInfoVersion.value}/org/apache/pekko/grpc/index.html")),
    (Test / testOptions) += Tests.Argument(TestFrameworks.ScalaTest, "-oDF"),
    crossScalaVersions := Seq(scala212, scala213, scala3),
    mimaReportSignatureProblems := true)

  override lazy val buildSettings = Seq(
    dynverSonatypeSnapshots := true)
}
