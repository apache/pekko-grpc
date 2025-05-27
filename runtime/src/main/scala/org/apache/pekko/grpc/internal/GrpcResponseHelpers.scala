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

package org.apache.pekko.grpc.internal

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.{ ActorSystem, ClassicActorSystemProvider }
import pekko.annotation.InternalApi
import pekko.grpc.GrpcProtocol.{ GrpcProtocolWriter, TrailerFrame }
import pekko.grpc.scaladsl.{ headers, GrpcExceptionHandler }
import pekko.grpc.{ ProtobufSerializer, Trailers }
import pekko.http.scaladsl.model.HttpEntity.ChunkStreamPart
import pekko.http.scaladsl.model.{ HttpEntity, HttpResponse, Trailer }
import pekko.stream.Materializer
import pekko.stream.scaladsl.Source
import io.grpc.Status

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

/**
 * Some helpers for creating HTTP entities for use with gRPC.
 *
 * INTERNAL API
 */
@InternalApi // consumed from generated classes so cannot be private
object GrpcResponseHelpers {
  private val TrailerOk = GrpcEntityHelpers.trailer(Status.OK)
  private val TrailerOkAttribute = Trailer(TrailerOk.trailers)

  def apply[T](e: Source[T, NotUsed])(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): HttpResponse =
    GrpcResponseHelpers(e, Source.single(TrailerOk))

  def apply[T](e: Source[T, NotUsed], eHandler: ActorSystem => PartialFunction[Throwable, Trailers])(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): HttpResponse =
    GrpcResponseHelpers(e, Source.single(TrailerOk), eHandler)

  def responseForSingleElement[T](e: T, eHandler: ActorSystem => PartialFunction[Throwable, Trailers])(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): HttpResponse = {
    val responseHeaders = headers.`Message-Encoding`(writer.messageEncoding.name) :: Nil
    try writer.encodeDataToResponse(m.serialize(e), responseHeaders, TrailerOkAttribute)
    catch {
      case NonFatal(ex) => status(GrpcEntityHelpers.handleException(ex, eHandler))
    }
  }

  def apply[T](e: Source[T, NotUsed], status: Future[Status])(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): HttpResponse =
    GrpcResponseHelpers(e, status, GrpcExceptionHandler.defaultMapper _)

  def apply[T](
      e: Source[T, NotUsed],
      status: Future[Status],
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers])(
      implicit m: ProtobufSerializer[T],
      mat: Materializer,
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): HttpResponse = {
    implicit val ec: ExecutionContext = mat.executionContext
    GrpcResponseHelpers(
      e,
      Source.lazyFuture(() => status.map(GrpcEntityHelpers.trailer(_))).mapMaterializedValue(_ => NotUsed),
      eHandler)
  }

  def apply[T](
      e: Source[T, NotUsed],
      trail: Source[TrailerFrame, NotUsed],
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers] = GrpcExceptionHandler.defaultMapper)(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): HttpResponse = {
    response(GrpcEntityHelpers(e, trail, eHandler))
  }

  private def response(entity: Source[ChunkStreamPart, NotUsed])(implicit writer: GrpcProtocolWriter) = {
    HttpResponse(
      headers = immutable.Seq(
        headers.`Message-Encoding`(writer.messageEncoding.name),
        // Pre-announcing trailers: See https://www.rfc-editor.org/rfc/rfc7230 #Section 4.4
        // Only relevant for Chunked transfer encoding.
        headers.`Trailer`(headers.`Status`.name)
      ),
      entity = HttpEntity.Chunked(writer.contentType, entity))
  }

  def status(trailer: Trailers)(implicit writer: GrpcProtocolWriter): HttpResponse = {
    HttpResponse(
      headers =
        headers.`Message-Encoding`(writer.messageEncoding.name) ::
        GrpcEntityHelpers.trailers(trailer.status, trailer.metadata),
      entity = HttpEntity.empty(writer.contentType)
    )
  }
}
