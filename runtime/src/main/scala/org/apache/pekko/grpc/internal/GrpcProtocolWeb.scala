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
import pekko.grpc.GrpcProtocol._
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.HttpEntity.{ Chunk, ChunkStreamPart }
import pekko.http.scaladsl.model.headers.RawHeader
import pekko.stream.scaladsl.Flow
import pekko.util.{ ByteString, ByteStringBuilder }
import io.grpc.{ Status, StatusException }
import scala.collection.immutable

abstract class GrpcProtocolWebBase(subType: String) extends AbstractGrpcProtocol(subType) {
  protected def postEncode(frame: ByteString): ByteString
  protected def preDecodeStrict(frame: ByteString): ByteString
  protected def preDecodeFlow: Flow[ByteString, ByteString, NotUsed]

  override protected def writer(codec: Codec): GrpcProtocolWriter =
    AbstractGrpcProtocol.writer(this, codec, frame => encodeFrame(codec, frame), encodeDataToResponse(codec))

  override protected def reader(codec: Codec): GrpcProtocolReader =
    AbstractGrpcProtocol.reader(codec, decodeFrame, preDecodeStrict, preDecodeFlow)

  private def encodeFrame(codec: Codec, frame: Frame): ChunkStreamPart =
    Chunk(postEncode(encodeFrameToBytes(codec, frame)))

  private def encodeDataToResponse(
      codec: Codec)(data: ByteString, headers: immutable.Seq[HttpHeader], trailer: Trailer): HttpResponse =
    HttpResponse(
      status = StatusCodes.OK,
      headers = headers,
      entity = HttpEntity(contentType, encodeDataToFrameBytes(codec, data, trailer)),
      protocol = HttpProtocols.`HTTP/1.1`)

  private def encodeDataToFrameBytes(codec: Codec, data: ByteString, trailer: Trailer): ByteString = {
    val trailerData = encodeTrailerHeaders(trailer.headers.iterator)
    val trailerFrame =
      AbstractGrpcProtocol.encodeFrameData(codec.compress(trailerData), codec.isCompressed, isTrailer = true)
    postEncode(encodeFrameToBytes(codec, DataFrame(data)) ++ trailerFrame)
  }

  private def encodeFrameToBytes(codec: Codec, frame: Frame): ByteString =
    frame match {
      case DataFrame(data) =>
        AbstractGrpcProtocol.encodeFrameData(codec.compress(data), codec.isCompressed, isTrailer = false)
      case TrailerFrame(trailer) =>
        AbstractGrpcProtocol.encodeFrameData(
          codec.compress(encodeTrailerHeaders(trailer.iterator.map(h => h.lowercaseName -> h.value))),
          codec.isCompressed,
          isTrailer = true)
    }

  private final def decodeFrame(frameHeader: Int, data: ByteString): Frame = {
    (frameHeader & 0x80) match {
      case 0    => DataFrame(data)
      case 0x80 => TrailerFrame(decodeTrailer(data))
      case f    => throw new StatusException(Status.INTERNAL.withDescription(s"Unknown frame type [$f]"))
    }
  }

  private final def encodeTrailerHeaders(trailerHeaders: Iterator[(String, String)]): ByteString = {
    val builder = new ByteStringBuilder
    while (trailerHeaders.hasNext) {
      val (header, value) = trailerHeaders.next()
      builder.append(ByteString(header.toLowerCase)).putByte(':').append(ByteString(value)).putByte('\r').putByte('\n')
    }
    builder.result()
  }

  private final def decodeTrailer(data: ByteString): List[HttpHeader] = {
    val str = data.utf8String
    str.split("\r\n").iterator.filter(_.nonEmpty).flatMap { line =>
      val colon = line.indexOf(':')
      if (colon > 0) {
        val key = line.substring(0, colon).trim
        val value = line.substring(colon + 1).trim
        if (key.nonEmpty) Some(RawHeader(key, value)) else None
      } else None
    }.toList
  }

}

/**
 * Implementation of the gRPC Web protocol.
 *
 * Protocol:
 *  - Data frames are encoded to a stream of `Chunk` as per the gRPC-web specification.
 *  - Trailer frames are encoded to a `Chunk` (containing a marked trailer frame) as per the gRPC-web specification.
 */
object GrpcProtocolWeb extends GrpcProtocolWebBase("grpc-web") {

  override final def postEncode(frame: ByteString): ByteString = frame

  override final def preDecodeStrict(frame: ByteString): ByteString = frame

  override final def preDecodeFlow: Flow[ByteString, ByteString, NotUsed] = Flow.apply
}

/**
 * The `application/grpc-web-text+proto` variant of gRPC.
 *
 * This is the same as `application/grpc-web+proto`, but with each chunk of the frame encoded gRPC data also base64 encoded.
 */
object GrpcProtocolWebText extends GrpcProtocolWebBase("grpc-web-text") {

  override final def postEncode(framed: ByteString): ByteString = framed.encodeBase64

  override final def preDecodeStrict(frame: ByteString): ByteString = frame.decodeBase64

  override final def preDecodeFlow: Flow[ByteString, ByteString, NotUsed] = DecodeBase64()
}
