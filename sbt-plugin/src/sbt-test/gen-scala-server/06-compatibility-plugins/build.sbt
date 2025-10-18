/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

enablePlugins(ProtocGoPlugin) // enable it first to test possibility of getting overriden

scalaVersion := "2.13.17"

enablePlugins(PekkoGrpcPlugin)
