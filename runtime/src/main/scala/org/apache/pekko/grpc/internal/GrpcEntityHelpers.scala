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
import pekko.NotUsed
import pekko.actor.{ ActorSystem, ClassicActorSystemProvider }
import pekko.annotation.InternalApi
import pekko.grpc.{ GrpcServiceException, ProtobufSerializer, Trailers }
import pekko.grpc.GrpcProtocol.{ DataFrame, Frame, GrpcProtocolWriter, TrailerFrame }
import pekko.grpc.scaladsl.{ headers, BytesEntry, Metadata, MetadataEntry, StringEntry }
import pekko.http.scaladsl.model.HttpEntity.ChunkStreamPart
import pekko.http.scaladsl.model.HttpHeader
import pekko.http.scaladsl.model.headers.RawHeader
import pekko.stream.scaladsl.{ Sink, Source }
import io.grpc.Status

import scala.concurrent.Future
import scala.util._

/** INTERNAL API */
@InternalApi
object GrpcEntityHelpers {
  def apply[T](
      e: Source[T, NotUsed],
      trail: Source[TrailerFrame, NotUsed],
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers])(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): Source[ChunkStreamPart, NotUsed] = {
    chunks(e, trail).recover {
      case t =>
        val e = handleException(t, eHandler)
        writer.encodeFrame(trailer(e.status, e.metadata))
    }
  }

  def atLeastOneElement[T](
      source: Source[T, NotUsed],
      trail: Source[TrailerFrame, NotUsed],
      errorHandler: ActorSystem => PartialFunction[Throwable, Trailers])(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider
  ): Future[Either[Trailers, Source[ChunkStreamPart, NotUsed]]] = {
    chunks(source, trail)
      .prefixAndTail(1)
      .runWith(Sink.head)
      .transformWith {
        case Failure(exception) => Future.successful(Left(handleException(exception, errorHandler)))
        case Success((head, tail)) =>
          Future.successful {
            Right(
              Source(head)
                .concat(tail)
                .recover {
                  case exception =>
                    val trailers = handleException(exception, errorHandler)
                    writer.encodeFrame(trailer(trailers.status, trailers.metadata))
                }
            )
          }
      }(system.classicSystem.dispatcher)
  }

  def handleException(t: Throwable, eHandler: ActorSystem => PartialFunction[Throwable, Trailers])(
      implicit system: ClassicActorSystemProvider): Trailers =
    eHandler(system.classicSystem).orElse[Throwable, Trailers] {
      case e: GrpcServiceException => Trailers(e.status, e.metadata)
      case e: Exception            => Trailers(Status.UNKNOWN.withCause(e).withDescription("Stream failed"))
    }(t)

  def apply[T](e: T)(implicit m: ProtobufSerializer[T], writer: GrpcProtocolWriter): Source[ChunkStreamPart, NotUsed] =
    chunks(Source.single(e), Source.empty)

  import pekko.stream._
  import pekko.stream.scaladsl._
  import scala.annotation.unchecked.uncheckedVariance
  // A faster implementation of concat that does not allocate so much
  private def concatCheap[U, Mat2](that: Graph[SourceShape[U], Mat2]): Graph[FlowShape[U @uncheckedVariance, U], Mat2] =
    GraphDSL.createGraph(that) { implicit b => r =>
      import GraphDSL.Implicits._
      val merge = b.add(new Concat[U](2))
      r ~> merge.in(1)
      FlowShape(merge.in(0), merge.out)
    }

  private def chunks[T](e: Source[T, NotUsed], trail: Source[Frame, NotUsed])(
      implicit m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter): Source[ChunkStreamPart, NotUsed] =
    e.map { msg => DataFrame(m.serialize(msg)) }.via(concatCheap(trail)).via(writer.frameEncoder)

  def trailer(status: Status): TrailerFrame =
    TrailerFrame(trailers = statusHeaders(status))

  def trailer(status: Status, metadata: Metadata): TrailerFrame =
    TrailerFrame(trailers = trailers(status, metadata))

  def trailers(status: Status, metadata: Metadata): List[HttpHeader] =
    statusHeaders(status) ++ metadataHeaders(metadata)

  def statusHeaders(status: Status): List[HttpHeader] =
    List(headers.`Status`(status.getCode.value.toString)) ++ Option(status.getDescription).map(d =>
      headers.`Status-Message`(d))

  def metadataHeaders(metadata: Metadata): List[HttpHeader] =
    metadataHeaders(metadata.asList)

  def metadataHeaders(metadata: List[(String, MetadataEntry)]): List[HttpHeader] =
    metadata.map {
      case (key, StringEntry(value)) => RawHeader(key, value)
      case (key, BytesEntry(value))  => RawHeader(key, MetadataImpl.encodeBinaryHeader(value))
    }
}
