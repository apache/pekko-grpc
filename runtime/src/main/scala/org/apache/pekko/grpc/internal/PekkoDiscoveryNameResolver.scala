/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.internal

import java.net.{ InetAddress, InetSocketAddress, UnknownHostException }

import org.apache.pekko
import pekko.discovery.ServiceDiscovery.ResolvedTarget
import pekko.discovery.{ Lookup, ServiceDiscovery }
import pekko.grpc.GrpcClientSettings
import io.grpc.{ Attributes, EquivalentAddressGroup, NameResolver, Status }
import io.grpc.NameResolver.Listener

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Promise }
import scala.util.{ Failure, Success }

class PekkoDiscoveryNameResolver(
    discovery: ServiceDiscovery,
    defaultPort: Int,
    serviceName: String,
    portName: Option[String],
    protocol: Option[String],
    resolveTimeout: FiniteDuration)(implicit val ec: ExecutionContext)
    extends NameResolver {
  override def getServiceAuthority: String = serviceName

  val listener: Promise[Listener] = Promise()

  override def start(l: Listener): Unit = {
    listener.trySuccess(l)
    lookup(l)
  }

  override def refresh(): Unit =
    listener.future.onComplete {
      case Success(l) => lookup(l)
      case Failure(_) => // We never fail this promise
    }

  def lookup(listener: Listener): Unit = {
    discovery.lookup(Lookup(serviceName, portName, protocol), resolveTimeout).onComplete {
      case Success(result) =>
        try {
          listener.onAddresses(addresses(result.addresses), Attributes.EMPTY)
        } catch {
          case e: UnknownHostException =>
            // TODO at least log
            listener.onError(Status.UNKNOWN.withDescription(e.getMessage))
        }
      case Failure(e) =>
        // TODO at least log
        listener.onError(Status.UNKNOWN.withDescription(e.getMessage))
    }
  }

  /**
   * Resolves each target to an address group. Targets whose host cannot be resolved are skipped,
   * so that one unresolvable host does not prevent the remaining targets from being used. Only
   * when every target fails to resolve is the first failure rethrown.
   */
  @throws[UnknownHostException]
  private def addresses(addresses: Seq[ResolvedTarget]) = {
    import scala.jdk.CollectionConverters._
    var firstFailure: Option[UnknownHostException] = None
    val groups = addresses.flatMap { target =>
      val port = target.port.getOrElse(defaultPort)
      try {
        val address = target.address.getOrElse(InetAddress.getByName(target.host))
        Some(new EquivalentAddressGroup(new InetSocketAddress(address, port)))
      } catch {
        case e: UnknownHostException =>
          if (firstFailure.isEmpty) firstFailure = Some(e)
          None
      }
    }
    firstFailure match {
      case Some(e) if groups.isEmpty => throw e
      case _                         => groups.asJava
    }
  }

  override def shutdown(): Unit = ()
}

object PekkoDiscoveryNameResolver {
  def apply(settings: GrpcClientSettings)(implicit ec: ExecutionContext): PekkoDiscoveryNameResolver =
    new PekkoDiscoveryNameResolver(
      settings.serviceDiscovery,
      settings.defaultPort,
      settings.serviceName,
      settings.servicePortName,
      settings.serviceProtocol,
      settings.resolveTimeout)
}
