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

import org.apache.pekko
import pekko.actor.{
  ActorSystem,
  ClassicActorSystemProvider,
  ExtendedActorSystem,
  Extension,
  ExtensionId,
  ExtensionIdProvider
}
import pekko.annotation.{ InternalApi, InternalStableApi }
import pekko.http.javadsl.model.{ HttpRequest, HttpResponse }

import scala.annotation.nowarn

/** INTERNAL API */
@InternalStableApi
private[internal] class TelemetryExtensionImpl(val spi: TelemetrySpi) extends Extension

/** INTERNAL API */
@InternalStableApi
object TelemetryExtension extends ExtensionId[TelemetryExtensionImpl] with ExtensionIdProvider {
  override def lookup = TelemetryExtension
  override def createExtension(system: ExtendedActorSystem) =
    new TelemetryExtensionImpl(TelemetrySpi(system))

  /** Java API */
  override def get(system: ActorSystem): TelemetryExtensionImpl = super.get(system)
  override def get(system: ClassicActorSystemProvider): TelemetryExtensionImpl = super.get(system)
}

private[internal] object TelemetrySpi {
  private val ConfigKey = "pekko.grpc.telemetry-class"
  def apply(system: ClassicActorSystemProvider): TelemetrySpi = {
    if (!system.classicSystem.settings.config.hasPath(ConfigKey)) NoOpTelemetry
    else {
      val fqcn = system.classicSystem.settings.config.getString(ConfigKey)
      try {
        system.classicSystem
          .asInstanceOf[ExtendedActorSystem]
          .dynamicAccess
          .createInstanceFor[TelemetrySpi](fqcn, Nil)
          .get
      } catch {
        case ex: Throwable =>
          system.classicSystem.log.debug(
            "{} references a class that could not be instantiated ({}) falling back to no-op implementation",
            fqcn,
            ex.toString)
          NoOpTelemetry
      }
    }
  }
}

@InternalStableApi
trait TelemetrySpi {
  @nowarn
  def onRequest[T <: HttpRequest](prefix: String, method: String, request: T): T = request

  @nowarn
  def onResponse[Req <: HttpRequest, Rep <: HttpResponse](prefix: String, method: String, request: Req, response: Rep): Rep = response
}

@InternalApi
private[internal] object NoOpTelemetry extends TelemetrySpi
