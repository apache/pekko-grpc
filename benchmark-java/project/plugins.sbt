/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

lazy val plugins = (project in file(".")).dependsOn(ProjectRef(file("../../"), "sbt-plugin"))
// Use this instead of above when importing to IDEA, after publishLocal and replacing the version here
//addSbtPlugin("org.apache.pekko" % "pekko-grpc-sbt-plugin" % "<version>")
