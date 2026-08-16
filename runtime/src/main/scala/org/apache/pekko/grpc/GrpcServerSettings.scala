/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.grpc

import org.apache.pekko
import pekko.actor.ClassicActorSystemProvider
import pekko.annotation.ApiMayChange
import com.typesafe.config.Config

object GrpcServerSettings {

  /**
   * Scala API: Create settings from the actor system's default configuration (`pekko.grpc.server`).
   */
  def apply(implicit actorSystem: ClassicActorSystemProvider): GrpcServerSettings =
    fromConfig(actorSystem.classicSystem.settings.config.getConfig("pekko.grpc.server"))

  /**
   * Java API: Create settings from the actor system's default configuration (`pekko.grpc.server`).
   */
  def create(actorSystem: ClassicActorSystemProvider): GrpcServerSettings =
    apply(actorSystem)

  /**
   * Create settings from a custom Config (must contain the same keys as `pekko.grpc.server`).
   */
  def fromConfig(config: Config): GrpcServerSettings =
    new GrpcServerSettings(
      maxInboundMessageSize = config.getInt("max-inbound-message-size"))
}

/**
 * Settings for gRPC server services.
 *
 * Read from `pekko.grpc.server` in the actor system's configuration.
 *
 * @since 2.0.0
 */
@ApiMayChange
final class GrpcServerSettings private (
    val maxInboundMessageSize: Int) {

  /**
   * Maximum allowed size for inbound gRPC messages (in bytes).
   * Applies to the decompressed message size. Messages exceeding this limit
   * will be rejected with RESOURCE_EXHAUSTED status.
   * @since 2.0.0
   */
  def withMaxInboundMessageSize(value: Int): GrpcServerSettings =
    new GrpcServerSettings(
      maxInboundMessageSize = value)
}
