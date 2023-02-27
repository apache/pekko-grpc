/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc

import java.util.concurrent.CompletionStage

import scala.compat.java8.FutureConverters._
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.ClassicActorSystemProvider
import org.apache.pekko.annotation.InternalStableApi
import org.apache.pekko.grpc.internal.{ ChannelUtils, InternalChannel }
import org.apache.pekko.grpc.scaladsl.Grpc

final class GrpcChannel private (
    @InternalStableApi val settings: GrpcClientSettings,
    @InternalStableApi val internalChannel: InternalChannel)(implicit sys: ClassicActorSystemProvider) {

  Grpc(sys).registerChannel(this)

  /**
   * Java API: Initiates a shutdown in which preexisting and new calls are cancelled.
   */
  def closeCS(): CompletionStage[Done] =
    close().toJava

  /**
   * Java API: Returns a CompletionStage that completes successfully when channel is shut down via close(),
   * or exceptionally if connection cannot be established or reestablished after maxConnectionAttempts.
   */
  def closedCS(): CompletionStage[Done] =
    closed().toJava

  /**
   * Scala API: Initiates a shutdown in which preexisting and new calls are cancelled.
   */
  def close(): Future[org.apache.pekko.Done] = {
    Grpc(sys).deregisterChannel(this)
    ChannelUtils.close(internalChannel)
  }

  /**
   * Scala API: Returns a Future that completes successfully when channel is shut down via close()
   * or exceptionally if a connection cannot be established or reestablished after maxConnectionAttempts.
   */
  def closed(): Future[org.apache.pekko.Done] =
    internalChannel.done
}

object GrpcChannel {
  def apply(settings: GrpcClientSettings)(implicit sys: ClassicActorSystemProvider): GrpcChannel = {
    new GrpcChannel(
      settings,
      ChannelUtils.create(settings, org.apache.pekko.event.Logging(sys.classicSystem, classOf[GrpcChannel])))
  }
}
