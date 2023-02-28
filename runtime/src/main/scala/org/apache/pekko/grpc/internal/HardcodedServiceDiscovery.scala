/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.internal

import org.apache.pekko
import pekko.discovery.{ Lookup, ServiceDiscovery }
import pekko.discovery.ServiceDiscovery.Resolved

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class HardcodedServiceDiscovery(resolved: Resolved) extends ServiceDiscovery {
  override def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] =
    Future.successful(resolved)
}
