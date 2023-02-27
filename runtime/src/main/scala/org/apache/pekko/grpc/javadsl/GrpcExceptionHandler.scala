/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.javadsl

import java.util.concurrent.CompletionException
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.ClassicActorSystemProvider
import org.apache.pekko.annotation.ApiMayChange
import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.grpc.{ GrpcServiceException, Trailers }
import org.apache.pekko.grpc.GrpcProtocol.GrpcProtocolWriter
import org.apache.pekko.grpc.internal.{ GrpcMetadataImpl, GrpcResponseHelpers, MissingParameterException }
import org.apache.pekko.http.javadsl.model.HttpResponse
import org.apache.pekko.japi.{ Function => jFunction }
import io.grpc.{ Status, StatusRuntimeException }

import scala.concurrent.ExecutionException
import org.apache.pekko.event.Logging

@ApiMayChange
object GrpcExceptionHandler {
  private val INTERNAL = Trailers(Status.INTERNAL)
  private val INVALID_ARGUMENT = Trailers(Status.INVALID_ARGUMENT)

  def defaultMapper: jFunction[ActorSystem, jFunction[Throwable, Trailers]] =
    new jFunction[ActorSystem, jFunction[Throwable, Trailers]] {
      override def apply(system: ActorSystem): jFunction[Throwable, Trailers] =
        default(system)
    }

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
          case other =>
            val log = Logging(system, "org.apache.pekko.grpc.javadsl.GrpcExceptionHandler")
            log.error(other, "Unhandled error: [{}]", other.getMessage)
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
