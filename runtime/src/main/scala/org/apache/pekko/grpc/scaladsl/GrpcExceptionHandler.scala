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

package org.apache.pekko.grpc.scaladsl

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.actor.ClassicActorSystemProvider
import pekko.annotation.{ ApiMayChange, InternalStableApi }
import pekko.grpc.{ GrpcServiceException, Trailers }
import pekko.grpc.GrpcProtocol.GrpcProtocolWriter
import pekko.grpc.internal.{ GrpcMetadataImpl, GrpcResponseHelpers, MissingParameterException }
import pekko.http.scaladsl.model.HttpResponse
import io.grpc.{ Status, StatusRuntimeException }
import org.apache.pekko.http.scaladsl.model.http2.PeerClosedStreamException

import scala.concurrent.{ ExecutionException, Future }
import pekko.event.Logging

@ApiMayChange
object GrpcExceptionHandler {
  private val INTERNAL = Trailers(Status.INTERNAL)
  private val INVALID_ARGUMENT = Trailers(Status.INVALID_ARGUMENT)

  private def log(system: ActorSystem) = Logging(system, "org.apache.pekko.grpc.scaladsl.GrpcExceptionHandler")

  def defaultMapper(system: ActorSystem): PartialFunction[Throwable, Trailers] = {
    case e: ExecutionException =>
      if (e.getCause == null) INTERNAL
      else defaultMapper(system)(e.getCause)
    case grpcException: GrpcServiceException => Trailers(grpcException.status, grpcException.metadata)
    case e: NotImplementedError              => Trailers(Status.UNIMPLEMENTED.withDescription(e.getMessage))
    case e: UnsupportedOperationException    => Trailers(Status.UNIMPLEMENTED.withDescription(e.getMessage))
    case _: MissingParameterException        => INVALID_ARGUMENT
    case e: StatusRuntimeException =>
      val meta = Option(e.getTrailers).getOrElse(new io.grpc.Metadata())
      Trailers(e.getStatus, new GrpcMetadataImpl(meta))
    case e: PeerClosedStreamException =>
      log(system).warning(e, "Peer closed the stream: [{}]", e.getMessage)
      INTERNAL
    case other =>
      log(system).error(other, "Unhandled error: [{}]", other.getMessage)
      INTERNAL
  }

  @InternalStableApi
  def default(
      implicit system: ClassicActorSystemProvider,
      writer: GrpcProtocolWriter): PartialFunction[Throwable, Future[HttpResponse]] =
    from(defaultMapper(system.classicSystem))

  @InternalStableApi
  def from(mapper: PartialFunction[Throwable, Trailers])(
      implicit system: ClassicActorSystemProvider,
      writer: GrpcProtocolWriter): PartialFunction[Throwable, Future[HttpResponse]] =
    mapper.orElse(defaultMapper(system.classicSystem)).andThen(s => Future.successful(GrpcResponseHelpers.status(s)))

}
