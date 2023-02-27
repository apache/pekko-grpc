/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.internal

import java.net.URI

import org.apache.pekko.discovery.ServiceDiscovery
import io.grpc.{ NameResolver, NameResolverProvider }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class PekkoDiscoveryNameResolverProvider(
    discovery: ServiceDiscovery,
    defaultPort: Int,
    portName: Option[String],
    protocol: Option[String],
    resolveTimeout: FiniteDuration)(implicit ec: ExecutionContext)
    extends NameResolverProvider {
  override def isAvailable: Boolean = true

  override def priority(): Int = 5

  override def getDefaultScheme: String = "http"

  override def newNameResolver(targetUri: URI, args: NameResolver.Args): PekkoDiscoveryNameResolver = {
    require(targetUri.getAuthority != null, s"target uri should not have null authority, got [$targetUri]")
    new PekkoDiscoveryNameResolver(discovery, defaultPort, targetUri.getAuthority, portName, protocol, resolveTimeout)
  }
}
