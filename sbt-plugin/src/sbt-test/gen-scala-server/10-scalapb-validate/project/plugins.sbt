/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

addSbtPlugin("org.apache.pekko" % "pekko-grpc-sbt-plugin" % sys.props("project.version"))

// ScalaPB Validate sbt plugin does not have a release that supports ScalaPB 1.0.0
Global / evictionErrorLevel := Level.Info

libraryDependencies ++= Seq("com.thesamet.scalapb" %% "scalapb-validate-codegen" % "0.3.6")
