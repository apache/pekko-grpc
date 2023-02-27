package org.apache.pekko.grpc

import sbt._

// helper to define projects that test the plugin infrastructure
object ProjectExtensions {
  implicit class AddPluginTest(project: Project) {

    /** Add settings to test the sbt-plugin in-process */
    def pluginTestingSettings: Project =
      project.dependsOn(ProjectRef(file("."), "pekko-grpc-runtime")).enablePlugins(
        org.apache.pekko.grpc.build.ReflectiveCodeGen)
  }
}
