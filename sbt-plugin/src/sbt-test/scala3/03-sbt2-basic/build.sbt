/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

// Verify that the pekko-grpc sbt plugin cross-builds correctly for sbt 2.x.
scalaVersion := sys.props.getOrElse(
  "pekko.grpc.scala3.next.version",
  sys.error("pekko.grpc.scala3.next.version must be provided by scriptedLaunchOpts"))

scalacOptions ++= Seq(
  "-Wconf:msg=Implicit parameters should be provided with a `using` clause:s",
  "-Wconf:msg=Ignoring \\[this\\] qualifier:s",
  "-Wconf:msg=`_` is deprecated for wildcard arguments of types:s")

enablePlugins(PekkoGrpcPlugin)

Compile / PB.protoSources += baseDirectory.value / "custom-protobuf"

lazy val checkProtoInputs = taskKey[Unit]("Check that protobuf files are watched recursively")

checkProtoInputs := {
  val base = baseDirectory.value
  val cases = Seq(
    (Compile / PB.generate / sbt.nio.Keys.fileInputs).value -> Seq(
      "src/main/protobuf/echo.proto",
      "src/main/protobuf/nested/new.proto",
      "src/main/proto/nested/new.proto",
      "custom-protobuf/nested/new.proto"),
    (Test / PB.generate / sbt.nio.Keys.fileInputs).value -> Seq(
      "src/test/protobuf/nested/new.proto",
      "src/test/proto/nested/new.proto"))

  cases.foreach { case (inputs, paths) =>
    paths.foreach { relative =>
      val path = (base / relative).toPath
      assert(inputs.exists(_.matches(path)), s"Protobuf file is not watched: $path")
      val nonProto = path.resolveSibling("README.md")
      assert(!inputs.exists(_.matches(nonProto)), s"Non-protobuf file is watched: $nonProto")
    }
  }
}
