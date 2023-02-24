/*
 * Copyright (C) 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.scaladsl

import scala.concurrent.Future
import scala.jdk.CollectionConverters._

import org.apache.pekko.Done
import org.apache.pekko.actor.{ CoordinatedShutdown, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import org.apache.pekko.annotation.InternalApi
import java.util.concurrent.ConcurrentHashMap

import org.apache.pekko.event.Logging
import org.apache.pekko.grpc.GrpcChannel

/** INTERNAL API */
@InternalApi
private[grpc] final class GrpcImpl(system: ExtendedActorSystem) extends Extension {
  private val channels = new ConcurrentHashMap[GrpcChannel, Unit]

  CoordinatedShutdown(system).addTask("before-actor-system-terminate", "close-grpc-channels") { () =>
    implicit val ec = system.dispatcher
    Future
      .sequence(
        channels
          .keySet()
          .asScala
          .map(channel =>
            channel.close().recover {
              case e =>
                val log = Logging(system, getClass)
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
