/*
 * Copyright (C) 2019-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.internal

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko.discovery.Lookup
import org.apache.pekko.discovery.ServiceDiscovery
import org.apache.pekko.discovery.ServiceDiscovery.Resolved
import org.apache.pekko.pattern.AskTimeoutException

class AlwaysTimingOutDiscovery extends ServiceDiscovery {
  def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] =
    Future.failed(new AskTimeoutException("Simulated timeout"))
}
