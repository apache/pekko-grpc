/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.internal

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.{ ActorSystem, ClassicActorSystemProvider }
import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.grpc.GrpcProtocol.{ GrpcProtocolWriter, TrailerFrame }
import org.apache.pekko.grpc.scaladsl.{ headers, GrpcExceptionHandler }
import org.apache.pekko.grpc.{ ProtobufSerializer, Trailers }
import org.apache.pekko.http.scaladsl.model.HttpEntity.ChunkStreamPart
import org.apache.pekko.http.scaladsl.model.{ HttpEntity, HttpResponse, Trailer }
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
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
      case NonFatal(ex) =>
        val trailers = GrpcEntityHelpers.handleException(ex, eHandler)
        writer.encodeDataToResponse(
          ByteString.empty,
          responseHeaders,
          Trailer(GrpcEntityHelpers.trailer(trailers.status, trailers.metadata).trailers))
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

  private def response[T](entity: Source[ChunkStreamPart, NotUsed])(implicit writer: GrpcProtocolWriter) = {
    HttpResponse(
      headers = immutable.Seq(headers.`Message-Encoding`(writer.messageEncoding.name)),
      entity = HttpEntity.Chunked(writer.contentType, entity))
  }

  def status(trailer: Trailers)(implicit writer: GrpcProtocolWriter): HttpResponse =
    response(Source.single(writer.encodeFrame(GrpcEntityHelpers.trailer(trailer.status, trailer.metadata))))
}