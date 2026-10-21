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
import pekko.http.scaladsl.util.FastFuture
import io.grpc.{ Status, StatusRuntimeException }
import org.apache.pekko.http.scaladsl.model.http2.PeerClosedStreamException

import scala.concurrent.{ ExecutionContext, ExecutionException, Future }
import scala.util.{ Failure, Success }
import scala.util.control.NonFatal
import pekko.event.Logging

@ApiMayChange
object GrpcExceptionHandler {
  private val INTERNAL = Trailers(Status.INTERNAL)
  private val INVALID_ARGUMENT = Trailers(Status.INVALID_ARGUMENT)
  private val UNIMPLEMENTED = Trailers(Status.UNIMPLEMENTED)

  private def log(system: ActorSystem) = Logging(system, "org.apache.pekko.grpc.scaladsl.GrpcExceptionHandler")

  def defaultMapper(system: ActorSystem): PartialFunction[Throwable, Trailers] = {
    case e: ExecutionException =>
      if (e.getCause == null) INTERNAL
      else defaultMapper(system)(e.getCause)
    case grpcException: GrpcServiceException => Trailers(grpcException.status, grpcException.metadata)
    case e: NotImplementedError              =>
      // the message is whatever the service implementation happened to throw with, so it is
      // logged rather than returned. Throw a GrpcServiceException to send a description on
      // purpose; that is what the generated handlers do for an unknown method.
      log(system).warning(e, "Unimplemented: [{}]", e.getMessage)
      UNIMPLEMENTED
    case e: UnsupportedOperationException =>
      log(system).warning(e, "Unimplemented: [{}]", e.getMessage)
      UNIMPLEMENTED
    case _: MissingParameterException => INVALID_ARGUMENT
    case e: StatusRuntimeException    =>
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
    mapper.orElse(defaultMapper(system.classicSystem)).andThen(s =>
      FastFuture.successful(GrpcResponseHelpers.status(s)))

  /**
   * Turns a failed response into a gRPC error response, using `eHandler` and falling back to the
   * [[defaultMapper]].
   *
   * Unlike `recoverWith(from(...))`, the exception handler is only constructed when the response has failed, and a
   * response that has already completed successfully is returned as is, so the successful path allocates nothing.
   * A response that has not completed yet is recovered on `ec`.
   */
  @InternalStableApi
  def recover(response: Future[HttpResponse], eHandler: ActorSystem => PartialFunction[Throwable, Trailers])(
      implicit system: ClassicActorSystemProvider,
      writer: GrpcProtocolWriter,
      ec: ExecutionContext): Future[HttpResponse] =
    response.value match {
      case Some(Success(_)) => response
      case Some(Failure(t)) =>
        try FastFuture.successful(errorResponse(t, eHandler))
        catch { case NonFatal(e) => FastFuture.failed(e) }
      case None => response.recover { case t => errorResponse(t, eHandler) }
    }

  private def errorResponse(t: Throwable, eHandler: ActorSystem => PartialFunction[Throwable, Trailers])(
      implicit system: ClassicActorSystemProvider,
      writer: GrpcProtocolWriter): HttpResponse = {
    val classicSystem = system.classicSystem
    GrpcResponseHelpers.status(eHandler(classicSystem).applyOrElse(t, defaultMapper(classicSystem)))
  }

}
