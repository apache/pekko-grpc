/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.scaladsl.tools

import java.net.InetSocketAddress

import org.apache.pekko.discovery.Lookup
import org.apache.pekko.discovery.ServiceDiscovery
import org.apache.pekko.discovery.ServiceDiscovery.Resolved
import org.apache.pekko.discovery.ServiceDiscovery.ResolvedTarget
import org.apache.pekko.http.scaladsl.Http

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
 * An In-Memory ServiceDiscovery that only can lookup "greeter"
 */
final class MutableServiceDiscovery(targets: List[InetSocketAddress]) extends ServiceDiscovery {
  var services: Future[Resolved] = _

  setServices(targets)

  def setServices(targets: List[InetSocketAddress]): Unit =
    services = Future.successful(
      Resolved(
        "greeter",
        targets.map(target => ResolvedTarget(target.getHostString, Some(target.getPort), Some(target.getAddress)))))

  override def lookup(query: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] = {
    require(query.serviceName == "greeter")
    services
  }
}

object MutableServiceDiscovery {
  def apply(targets: List[Http.ServerBinding]) = new MutableServiceDiscovery(targets.map(_.localAddress))
}
