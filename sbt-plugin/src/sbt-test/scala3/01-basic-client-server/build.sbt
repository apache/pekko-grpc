/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

scalaVersion := sys.props.getOrElse(
  "pekko.grpc.scala3.next.version",
  sys.error("pekko.grpc.scala3.next.version must be provided by scriptedLaunchOpts"))

scalacOptions ++= Seq(
  "-Werror",
  "-Wconf:msg=Implicit parameters should be provided with a `using` clause:s",
  "-Wconf:msg=Ignoring \\[this\\] qualifier:s",
  "-Wconf:msg=`_` is deprecated for wildcard arguments of types:s")

enablePlugins(PekkoGrpcPlugin)

lazy val checkResourceDirectories = taskKey[Unit]("Check that resource directories are unique")

checkResourceDirectories := {
  val compileDirectories = (Compile / unmanagedResourceDirectories).value
  val testDirectories = (Test / unmanagedResourceDirectories).value
  assert(compileDirectories == compileDirectories.distinct,
    s"Duplicate Compile resource directories: $compileDirectories")
  assert(testDirectories == testDirectories.distinct, s"Duplicate Test resource directories: $testDirectories")
}

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.20" % Test)
