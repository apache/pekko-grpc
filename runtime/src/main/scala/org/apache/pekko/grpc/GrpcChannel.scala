/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc

import java.util.concurrent.CompletionStage

import scala.concurrent.Future

import org.apache.pekko
import pekko.Done
import pekko.actor.ClassicActorSystemProvider
import pekko.annotation.InternalStableApi
import pekko.grpc.internal.{ ChannelUtils, InternalChannel }
import pekko.grpc.scaladsl.Grpc
import pekko.util.FutureConverters._

final class GrpcChannel private (
    @InternalStableApi val settings: GrpcClientSettings,
    @InternalStableApi val internalChannel: InternalChannel)(implicit sys: ClassicActorSystemProvider) {

  Grpc(sys).registerChannel(this)

  /**
   * Java API: Initiates a shutdown in which preexisting and new calls are cancelled.
   */
  def closeCS(): CompletionStage[Done] =
    close().asJava

  /**
   * Java API: Returns a CompletionStage that completes successfully when channel is shut down via close(),
   * or exceptionally if connection cannot be established or reestablished after maxConnectionAttempts.
   */
  def closedCS(): CompletionStage[Done] =
    closed().asJava

  /**
   * Scala API: Initiates a shutdown in which preexisting and new calls are cancelled.
   */
  def close(): Future[pekko.Done] = {
    Grpc(sys).deregisterChannel(this)
    ChannelUtils.close(internalChannel)
  }

  /**
   * Scala API: Returns a Future that completes successfully when channel is shut down via close()
   * or exceptionally if a connection cannot be established or reestablished after maxConnectionAttempts.
   */
  def closed(): Future[pekko.Done] =
    internalChannel.done
}

object GrpcChannel {
  def apply(settings: GrpcClientSettings)(implicit sys: ClassicActorSystemProvider): GrpcChannel = {
    new GrpcChannel(
      settings,
      ChannelUtils.create(settings, pekko.event.Logging(sys.classicSystem, classOf[GrpcChannel])))
  }
}
