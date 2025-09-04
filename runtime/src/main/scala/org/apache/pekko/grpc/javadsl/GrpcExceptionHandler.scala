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

package org.apache.pekko.grpc.javadsl

import java.util.concurrent.CompletionException
import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.actor.ClassicActorSystemProvider
import pekko.annotation.ApiMayChange
import pekko.annotation.InternalApi
import pekko.grpc.{ GrpcServiceException, Trailers }
import pekko.grpc.GrpcProtocol.GrpcProtocolWriter
import pekko.grpc.internal.{ GrpcMetadataImpl, GrpcResponseHelpers, MissingParameterException }
import pekko.http.javadsl.model.HttpResponse
import pekko.japi.function.{ Function => jFunction }
import io.grpc.{ Status, StatusRuntimeException }

import org.apache.pekko.http.scaladsl.model.http2.PeerClosedStreamException

import scala.concurrent.ExecutionException
import pekko.event.Logging

@ApiMayChange
object GrpcExceptionHandler {
  private val INTERNAL = Trailers(Status.INTERNAL)
  private val INVALID_ARGUMENT = Trailers(Status.INVALID_ARGUMENT)

  def defaultMapper: jFunction[ActorSystem, jFunction[Throwable, Trailers]] =
    new jFunction[ActorSystem, jFunction[Throwable, Trailers]] {
      override def apply(system: ActorSystem): jFunction[Throwable, Trailers] =
        default(system)
    }

  private def log(system: ActorSystem) = Logging(system, "org.apache.pekko.grpc.javadsl.GrpcExceptionHandler")

  /** INTERNAL API */
  @InternalApi
  private def default(system: ActorSystem): jFunction[Throwable, Trailers] =
    new jFunction[Throwable, Trailers] {
      override def apply(param: Throwable): Trailers =
        param match {
          case e: ExecutionException =>
            if (e.getCause == null) INTERNAL
            else default(system)(e.getCause)
          case e: CompletionException =>
            if (e.getCause == null) INTERNAL
            else default(system)(e.getCause)
          case grpcException: GrpcServiceException => Trailers(grpcException.status, grpcException.metadata)
          case _: MissingParameterException        => INVALID_ARGUMENT
          case e: NotImplementedError              => Trailers(Status.UNIMPLEMENTED.withDescription(e.getMessage))
          case e: UnsupportedOperationException    => Trailers(Status.UNIMPLEMENTED.withDescription(e.getMessage))
          case e: StatusRuntimeException           => Trailers(e.getStatus, new GrpcMetadataImpl(e.getTrailers))
          case e: PeerClosedStreamException =>
            log(system).warning(e, "Peer closed the stream: [{}]", e.getMessage)
            INTERNAL
          case other =>
            log(system).error(other, "Unhandled error: [{}]", other.getMessage)
            INTERNAL
        }
    }

  def standard(t: Throwable, writer: GrpcProtocolWriter, system: ClassicActorSystemProvider): HttpResponse =
    standard(t, default, writer, system)

  def standard(
      t: Throwable,
      mapper: jFunction[ActorSystem, jFunction[Throwable, Trailers]],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): HttpResponse =
    GrpcResponseHelpers.status(mapper(system.classicSystem)(t))(writer)
}
