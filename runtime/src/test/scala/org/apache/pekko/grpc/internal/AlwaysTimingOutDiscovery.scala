/*
 * Copyright (C) 2019-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.internal

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko
import pekko.discovery.Lookup
import pekko.discovery.ServiceDiscovery
import pekko.discovery.ServiceDiscovery.Resolved
import pekko.pattern.AskTimeoutException

class AlwaysTimingOutDiscovery extends ServiceDiscovery {
  def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] =
    Future.failed(new AskTimeoutException("Simulated timeout"))
}
