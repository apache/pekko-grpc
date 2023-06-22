/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.interop.app

import org.apache.pekko.grpc.interop.PekkoHttpServerProviderScala

/**
 * Scala application that starts a web server at localhost serving the test
 * application used for the gRPC integration tests.
 *
 * This can be useful for 'manually' interacting with this server.
 *
 * You can start this app from sbt with 'interop-tests/test:reStart'
 */
object PekkoHttpServerAppScala extends App {
  val (sys, binding) = PekkoHttpServerProviderScala.server.start(Array())
  sys.log.info(s"Bound to ${binding.localAddress}")
}
