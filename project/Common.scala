/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
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
  val isScala38OrLater = Def.setting(CrossVersion.partialVersion(scalaVersion.value).exists {
    case (3, minor) if minor >= 8 => true
    case _                        => false
  })

  private val scala38WarningOptions = Seq(
    "-Wconf:msg=Implicit parameters should be provided with a `using` clause:silent",
    "-Wconf:msg=The trailing .* for eta-expansion is unnecessary:silent",
    "-Wconf:msg=Usage of implicit .* is not accessible here:silent")

  override def globalSettings =
    Seq(
      homepage := Some(url("https://pekko.apache.org/")),
      scmInfo := Some(ScmInfo(url("https://github.com/apache/pekko-grpc"),
        "git@github.com:apache/pekko-grpc")),
      developers += Developer(
        "contributors",
        "Contributors",
        "dev@pekko.apache.org",
        url("https://github.com/apache/pekko-grpc/graphs/contributors")),
      description :=
        "Apache Pekko gRPC - Support for building streaming gRPC servers and clients on top of Pekko Streams.")

  override lazy val projectSettings = Seq(
    projectInfoVersion := (if (isSnapshot.value) "snapshot" else version.value),
    scalacOptions ++=
      (if (!isScala3.value)
         Seq(
           "-unchecked",
           "-deprecation",
           "-language:_",
           "-Xfatal-warnings",
           "-Ywarn-unused",
           "-encoding",
           "UTF-8") ++
         (if (scalaVersion.value.startsWith("2.13.")) Seq("-Xsource:3") else Seq.empty)
       else
         Seq("-unchecked", "-deprecation", "-Werror", "-Wunused:imports", "-encoding", "UTF-8") ++
         (if (isScala38OrLater.value) scala38WarningOptions else Seq.empty) ++
         (if (scalaVersion.value.startsWith("3.3.")) Seq("-Yfuture-lazy-vals") else Seq.empty)),
    Compile / scalacOptions ++=
      (if (!isScala3.value)
         Seq(
           // Generated code for methods/fields marked 'deprecated'
           "-Wconf:msg=Marked as deprecated in proto file:silent",
           // ignore imports in templates (FIXME why is that trailing .* needed?)
           "-Wconf:src=.*.txt.*:silent",
           "-Wconf:cat=unused-nowarn:silent")
       else
         Seq(
           // Generated code for methods/fields marked 'deprecated'
           "-Wconf:msg=Marked as deprecated in proto file:silent",
           "-Wconf:msg=unused import:silent",
           "-Wconf:msg=transient key .* is excluded from the cache input:silent",
           "-Wconf:cat=feature:silent")),
    Compile / console / scalacOptions ~= (_.filterNot(consoleDisabledOptions.contains)),
    javacOptions ++= List("-Xlint:unchecked", "-Xlint:deprecation"),
    javacOptions := {
      val options = javacOptions.value
      if (isScala38OrLater.value) options.filterNot(_.startsWith("-Xlint")) ++ Seq("-nowarn", "-Xlint:none")
      else options
    },
    Compile / compile / javacOptions ++= Seq("--release", "17"),
    Compile / compile / scalacOptions ++= Seq("-release", "17"),
    Test / compile / scalacOptions ++= Seq("-release", "17"),
    Compile / doc / scalacOptions := scalacOptions.value ++ Seq(
      "-doc-title",
      "Apache Pekko gRPC",
      "-doc-version",
      version.value,
      "-sourcepath",
      (ThisBuild / baseDirectory).value.toString,
      "-doc-source-url", {
        val branch = if (isSnapshot.value) "main" else s"v${version.value}"
        s"https://github.com/apache/pekko-grpc/tree/${branch}€{FILE_PATH_EXT}#L€{FILE_LINE}"
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
