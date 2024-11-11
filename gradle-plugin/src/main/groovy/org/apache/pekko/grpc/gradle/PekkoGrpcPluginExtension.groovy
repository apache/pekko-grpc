/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.grpc.gradle


import org.gradle.api.Project

class PekkoGrpcPluginExtension {

    static final String PROTOC_VERSION = "4.28.2" // checked synced by VersionSyncCheckPlugin

    static final String PROTOC_PLUGIN_SCALA_VERSION = "2.12"

    static final String GRPC_VERSION = "1.67.1" // checked synced by VersionSyncCheckPlugin

    static final String PLUGIN_CODE = 'org.apache.pekko.grpc.gradle'

    // workaround for tests, where there's no jar and MANIFEST.MF can't be read
    final String pluginVersion = System.getProperty("pekkoGrpcTest.pluginVersion", PekkoGrpcPlugin.class.package.implementationVersion)

    boolean generateClient = true
    boolean generateServer = true
    boolean generatePlay = false
    boolean serverPowerApis = false
    boolean usePlayActions = false
    boolean includeStdTypes = false

    List<String> extraGenerators = []

    private final Project project

    final boolean scala

    PekkoGrpcPluginExtension(Project project) {
        this.project = project
        def scalaFiles = project.fileTree("src/main").matching { include "**/*.scala" }
        if (!scalaFiles.isEmpty()) {
            project.logger.info("Detected ${scalaFiles.size()} Scala source files. Plugin works in `scala` mode.")
            project.logger.debug("Scala files ${scalaFiles.files}")
        } else {
            project.logger.info("No Scala source files detected. Plugin works in `java` mode.")
        }
        this.scala = !scalaFiles.isEmpty()
    }
}
