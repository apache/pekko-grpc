/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.scaladsl

import scala.concurrent.{ ExecutionContext, Future }
import scala.jdk.CollectionConverters._

import org.apache.pekko
import pekko.Done
import pekko.actor.{ CoordinatedShutdown, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import pekko.annotation.InternalApi
import java.util.concurrent.ConcurrentHashMap

import pekko.event.{ LogSource, Logging }
import pekko.grpc.GrpcChannel

/** INTERNAL API */
@InternalApi
private[grpc] final class GrpcImpl(system: ExtendedActorSystem) extends Extension {
  private val channels = new ConcurrentHashMap[GrpcChannel, Unit]

  CoordinatedShutdown(system).addTask("before-actor-system-terminate", "close-grpc-channels") { () =>
    implicit val ec: ExecutionContext = system.dispatcher
    Future
      .sequence(
        channels
          .keySet()
          .asScala
          .map(channel =>
            channel.close().recover {
              case e =>
                val log = Logging(system, getClass)(LogSource.fromClass)
                log.warning("Failed to gracefully close {}, proceeding with shutdown anyway. {}", channel, e)
                Done
            }))
      .map(_ => Done)
  }

  /** INTERNAL API */
  @InternalApi
  def registerChannel(channel: GrpcChannel): Unit =
    channels.put(channel, ())

  /** INTERNAL API */
  @InternalApi
  def deregisterChannel(channel: GrpcChannel): Unit =
    channels.remove(channel)

}

/** INTERNAL API */
@InternalApi
private[grpc] object Grpc extends ExtensionId[GrpcImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): GrpcImpl = new GrpcImpl(system)

  override def lookup: ExtensionId[_ <: Extension] = Grpc
}
