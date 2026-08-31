/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException
import java.nio.charset.StandardCharsets

import sbt._
import sbt.io.Using
import sbt.Keys._
import sbtassembly.AssemblyKeys._
import org.mdedetrich.apache.sonatype.ApacheSonatypePlugin.autoImport.{
  apacheSonatypeLicenseFile,
  apacheSonatypeNoticeFile
}

/**
 * Guards the LICENSE and NOTICE files that ship in the assembly jars.
 *
 * The assembly jars bundle classes from 3rd party projects, so their `META-INF/LICENSE` and
 * `META-INF/NOTICE` have to account for those projects. Those files are curated by hand in
 * `legal/`, because most of the bundled jars carry no NOTICE of their own and the text has to be
 * taken from the upstream projects. These tasks stop the curated files from drifting away from
 * what the jars actually bundle.
 */
object AssemblyLicenseCheck {

  val assemblyBundledModules =
    taskKey[Seq[String]]("The `organization:name` ids of the 3rd party modules bundled into the assembly jar")
  val assemblyLicenseCheck =
    taskKey[Unit]("Check that every module bundled into the assembly jar is named in the assembly LICENSE file")
  val assemblyMetaInfArchives =
    taskKey[Seq[File]]("The published archives that should carry the assembly LICENSE and NOTICE files")
  val assemblyMetaInfCheck =
    taskKey[Unit]("Check that the published archives ship the assembly LICENSE and NOTICE files in META-INF")

  /**
   * Text that has to appear in the shipped files. Matching the curated file byte for byte only says
   * the merge strategy picked our copy; these markers say that copy is still the file it should be,
   * and not one that has been emptied or truncated.
   */
  private val requiredContent = Map(
    "META-INF/LICENSE" -> Seq(
      "Apache License",
      "Version 2.0, January 2004",
      "TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION",
      // the 3rd party section this file exists for, see legal/AssemblyLicense.txt
      "contain classes from 3rd party projects",
      "Apache License Version 2.0:"),
    "META-INF/NOTICE" -> Seq(
      "Apache Pekko gRPC",
      "The Apache Software Foundation",
      // the 3rd party section this file exists for, see legal/AssemblyNotice.txt
      "contain classes from 3rd party projects"))

  /** A module id as listed in the assembly LICENSE file: `organization:name`, optionally `:version`. */
  private val ModuleId = """^([a-zA-Z0-9][\w.\-]*):([a-zA-Z0-9][\w.\-]*)(?::.*)?$""".r

  lazy val settings: Seq[Setting[?]] = Seq(
    assemblyBundledModules := {
      val suffix = "_" + CrossVersion.binaryScalaVersion(scalaVersion.value)
      val modules = (assembly / fullClasspath).value.flatMap(_.get(moduleID.key))
      modules
        .filterNot(_.organization == "org.apache.pekko") // our own modules, covered by the ASF header
        .map { module =>
          val name = if (module.name.endsWith(suffix)) module.name.dropRight(suffix.length) else module.name
          s"${module.organization}:$name"
        }
        .distinct
        .sorted
    },
    assemblyLicenseCheck := {
      val log = streams.value.log
      val licenseFile = apacheSonatypeLicenseFile.value
      val listed = IO.readLines(licenseFile).map(_.trim).collect { case ModuleId(org, module) => s"$org:$module" }.toSet
      val bundled = assemblyBundledModules.value

      val extra = (listed -- bundled).toSeq.sorted
      if (extra.nonEmpty)
        // the assembly jars are cross built, so a module can be listed for a Scala version other than this one
        log.warn(
          s"${licenseFile.getName} lists modules that ${name.value} does not bundle for Scala ${scalaVersion.value}, " +
          s"check whether they are still needed: ${extra.mkString(", ")}")

      val missing = bundled.filterNot(listed)
      if (missing.nonEmpty)
        sys.error(
          s"${name.value} bundles modules that ${licenseFile.getName} does not account for: ${missing.mkString(", ")}")

      log.info(s"${licenseFile.getName} accounts for all ${bundled.size} modules bundled by ${name.value}")
    },
    assemblyMetaInfArchives := Seq(assembly.value),
    assemblyMetaInfCheck := {
      val log = streams.value.log
      val expected =
        Seq("META-INF/LICENSE" -> apacheSonatypeLicenseFile.value, "META-INF/NOTICE" -> apacheSonatypeNoticeFile.value)

      assemblyMetaInfArchives.value.foreach { archive =>
        val problems =
          try Using.zipFile(archive) { zip =>
              expected.flatMap { case (path, file) =>
                Option(zip.getEntry(path)) match {
                  case None =>
                    Some(s"$path is missing, expected a copy of $file")
                  case Some(entry) =>
                    val actual = Using.zipEntry(zip)(entry)(_.readAllBytes())
                    if (!actual.sameElements(IO.readBytes(file))) Some(s"$path does not match $file")
                    else {
                      val text = new String(actual, StandardCharsets.UTF_8)
                      val absent = requiredContent.getOrElse(path, Nil).filterNot(text.contains)
                      if (absent.isEmpty) None
                      else Some(s"$path does not mention ${absent.map(marker => s"'$marker'").mkString(", ")}")
                    }
                }
              }
            }
          catch {
            // the archive name is not part of the messages the zip classes throw
            case e: IOException => sys.error(s"${archive.getName} could not be read as a zip archive: $e")
          }

        if (problems.nonEmpty)
          sys.error(s"${archive.getName} has license problems: ${problems.mkString("; ")}")

        log.info(s"${archive.getName} ships ${expected.map(_._1).mkString(" and ")}")
      }
    })

}
