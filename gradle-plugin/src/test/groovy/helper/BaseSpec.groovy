/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package helper

import org.apache.pekko.grpc.gradle.PekkoGrpcPluginExtension
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static org.apache.pekko.grpc.gradle.PekkoGrpcPluginExtension.getPLUGIN_CODE

abstract class BaseSpec extends Specification {

    @Rule
    public final TemporaryFolder projectDir = new TemporaryFolder()

    File srcDir

    File buildDir

    File buildFile

    def createBuildFolder() {
        srcDir = projectDir.newFolder("src", "main", "proto")
        buildDir = projectDir.newFolder("build")
    }

    def generateBuildScripts() {
        buildFile = projectDir.newFile("build.gradle")
        buildFile.text = """
plugins {
  id '$PLUGIN_CODE'
}
project.dependencies {
    implementation "com.typesafe.scala-logging:scala-logging_2.13:3.9.2"
}
"""
    }

    def findPekkoGrpcRuntime() {
        this.project.configurations.pekkoGrpcRuntime.allDependencies.find { it.name.contains("pekko-grpc-runtime") }
    }

    PekkoGrpcPluginExtension sampleSetup(def plugin = "scala", String scala = "2.13") {
        if (plugin == "scala" || plugin == ScalaWrapperPlugin) {
            def scalaDir = projectDir.newFolder('src', 'main', 'scala')
            new File(scalaDir, "test.scala").text = "object PekkoGrpc"
        }

        project.pluginManager.apply PLUGIN_CODE
        project.dependencies {
            implementation "com.typesafe.scala-logging:scala-logging_$scala:3.9.2"
        }
        project.extensions.getByType(PekkoGrpcPluginExtension)
    }
}
