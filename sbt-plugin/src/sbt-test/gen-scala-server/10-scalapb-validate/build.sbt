/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

//#setup
import scalapb.GeneratorOption._

scalaVersion := "2.13.18"

// ScalaPB Validate sbt plugin does not have a release that supports ScalaPB 1.0.0
Global / evictionErrorLevel := Level.Info

enablePlugins(PekkoGrpcPlugin)

libraryDependencies +=
  "com.thesamet.scalapb" %% "scalapb-validate-core" % scalapb.validate.compiler.BuildInfo.version % "protobuf"
Compile / PB.targets +=
  scalapb.validate.gen(FlatPackage) -> (Compile / pekkoGrpcCodeGeneratorSettings / target).value
//#setup
