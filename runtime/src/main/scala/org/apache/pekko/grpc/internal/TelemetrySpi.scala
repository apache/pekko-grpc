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

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.{ActorSystem, ClassicActorSystemProvider, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import org.apache.pekko.annotation.{InternalApi, InternalStableApi}
import org.apache.pekko.grpc.Trailers
import org.apache.pekko.http.javadsl.model.{HttpRequest, HttpResponse}
import org.apache.pekko.japi.Function
import org.apache.pekko.stream.scaladsl.Source

import scala.annotation.nowarn
import scala.concurrent.{ExecutionContext, Future}

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
  def onResponse[Req <: HttpRequest, Rep <: HttpResponse](prefix: String, method: String, request: Req, response: Rep)
      : Rep = response
}

@InternalApi
private[internal] object NoOpTelemetry extends TelemetrySpi

/** INTERNAL API */
@InternalStableApi
private[internal] class TelemetryListenerExtensionImpl(val spi: TelemetryListenerSpi) extends Extension

/** INTERNAL API */
@InternalStableApi
object TelemetryListenerExtension extends ExtensionId[TelemetryListenerExtensionImpl] with ExtensionIdProvider {
  override def lookup = TelemetryListenerExtension
  override def createExtension(system: ExtendedActorSystem) =
    new TelemetryListenerExtensionImpl(TelemetryListenerSpi(system))

  /** Java API */
  override def get(system: ActorSystem): TelemetryListenerExtensionImpl = super.get(system)
  override def get(system: ClassicActorSystemProvider): TelemetryListenerExtensionImpl = super.get(system)
}

object TelemetryListener {}

trait TelemetryListener {
  def onStart(request: HttpRequest): request.type = request

  def onSendMessage[T](sent: T): T = sent
  def onSendMessage[T](source: Source[T, NotUsed]): Source[T, NotUsed] = source.map((m: T) => onSendMessage(m))

  def onReceiveMessage[T](received: T): T = received
  def onReceiveMessage[T](future: Future[T])(implicit ec: ExecutionContext): Future[T] =
    future.map((m: T) => onReceiveMessage(m))(ec)
  def onReceiveMessage[T](source: Source[T, NotUsed]): Source[T, NotUsed] = source.map((m: T) => onReceiveMessage(m))

  def onClose(trailers: Trailers): trailers.type = trailers

  def handleError[T](fn: T => PartialFunction[Throwable, Trailers]): T => PartialFunction[Throwable, Trailers] =
    in => fn(in).andThen(t => onClose(t))

  def handleError[T](fn: Function[T, Function[Throwable, Trailers]]): Function[T, Function[Throwable, Trailers]] = in => { throwable =>
    onClose(fn.apply(in).apply(throwable))
  }

  def onComplete(response: HttpResponse): response.type = response
}

object NoOpTelemetryListener extends TelemetryListener

trait TelemetryListenerSpi {
  def server(): TelemetryListener
  def client(): TelemetryListener
}

object TelemetryListenerSpi {
  private val fallbackConfigKey = "pekko.grpc.telemetry-listener-class"

  private def apply(configKey: String, system: ActorSystem) = {
    val config = system.classicSystem.settings.config

    val fqcn = if (config.hasPath(configKey)) Some(config.getString(configKey))
    else if (config.hasPath(fallbackConfigKey)) Some(config.getString(fallbackConfigKey))
    else None

    if (fqcn.isEmpty) NoOpTelemetryListener
    else {
      try {
        system.classicSystem
          .asInstanceOf[ExtendedActorSystem]
          .dynamicAccess
          .createInstanceFor[TelemetryListener](fqcn.get, Nil)
          .get
      } catch {
        case ex: Throwable =>
          system.classicSystem.log.debug(
            "{} references a class that could not be instantiated ({}) falling back to no-op implementation",
            fqcn.get,
            ex.toString)
          NoOpTelemetryListener
      }
    }
  }

  def apply(system: ActorSystem): TelemetryListenerSpi = new TelemetryListenerSpi {
    override def server(): TelemetryListener = apply("pekko.grpc.telemetry-listener-server-class", system)

    override def client(): TelemetryListener = apply("pekko.grpc.telemetry-listener-client-class", system)
  }
}
