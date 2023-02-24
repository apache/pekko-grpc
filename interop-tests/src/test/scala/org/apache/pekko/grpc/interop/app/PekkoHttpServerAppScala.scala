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
 * You can start this app from sbt with 'pekko-grpc-interop-tests/test:reStart'
 */
object PekkoHttpServerAppScala extends App {
  val (sys, binding) = PekkoHttpServerProviderScala.server.start(Array())
  sys.log.info(s"Bound to ${binding.localAddress}")
}
