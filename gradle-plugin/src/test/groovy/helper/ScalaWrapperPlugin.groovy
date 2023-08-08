/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package helper

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.scala.ScalaPlugin

class ScalaWrapperPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply ScalaPlugin
    }
}
