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
import pekko.annotation.{ ApiMayChange, InternalApi }
import pekko.grpc.internal.AbstractGrpcProtocol

import com.typesafe.config.{ Config, ConfigFactory }

object GrpcServerSettings {

  /**
   * INTERNAL API
   *
   * The built-in defaults, for entry points that have no access to the actor system's configuration.
   */
  @InternalApi
  private[grpc] lazy val defaults: GrpcServerSettings = fromConfig(ConfigFactory.empty())

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
   *
   * Keys that are absent fall back on the built-in defaults, so a hand-assembled `Config` that
   * does not resolve against `reference.conf` keeps working.
   */
  def fromConfig(config: Config): GrpcServerSettings =
    new GrpcServerSettings(
      maxInboundMessageSize =
        if (config.hasPath("max-inbound-message-size")) config.getInt("max-inbound-message-size")
        else AbstractGrpcProtocol.DefaultMaxInboundMessageSize,
      enforceDeadline =
        if (config.hasPath("enforce-deadline")) config.getBoolean("enforce-deadline")
        else true)
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
    val maxInboundMessageSize: Int,
    val enforceDeadline: Boolean) {

  require(
    maxInboundMessageSize > 0,
    s"maxInboundMessageSize must be positive, was [$maxInboundMessageSize]")

  /**
   * Maximum allowed size for inbound gRPC messages (in bytes).
   * Applies to the decompressed message size. Messages exceeding this limit
   * will be rejected with RESOURCE_EXHAUSTED status.
   * @since 2.0.0
   */
  def withMaxInboundMessageSize(value: Int): GrpcServerSettings =
    copy(maxInboundMessageSize = value)

  /**
   * Whether generated handlers bound a call by the deadline the client sent in `grpc-timeout`.
   *
   * When enabled (the default), a call that is still running when the deadline passes is
   * completed with `DEADLINE_EXCEEDED`, and a streamed response is ended and its `Source`
   * cancelled. When disabled, the header is ignored and the call runs to completion, as it did
   * before 2.0.0.
   * @since 2.0.0
   */
  def withEnforceDeadline(value: Boolean): GrpcServerSettings =
    copy(enforceDeadline = value)

  private def copy(
      maxInboundMessageSize: Int = maxInboundMessageSize,
      enforceDeadline: Boolean = enforceDeadline): GrpcServerSettings =
    new GrpcServerSettings(maxInboundMessageSize = maxInboundMessageSize, enforceDeadline = enforceDeadline)

  override def toString: String =
    s"GrpcServerSettings(maxInboundMessageSize=$maxInboundMessageSize, enforceDeadline=$enforceDeadline)"
}
