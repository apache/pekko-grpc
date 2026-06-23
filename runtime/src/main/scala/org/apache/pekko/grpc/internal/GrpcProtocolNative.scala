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
import pekko.grpc.GrpcProtocol._
import pekko.http.scaladsl.model.HttpEntity.{ Chunk, ChunkStreamPart, LastChunk }
import pekko.http.scaladsl.model.{
  AttributeKey,
  AttributeKeys,
  HttpEntity,
  HttpHeader,
  HttpProtocols,
  HttpResponse,
  StatusCodes,
  Trailer
}
import pekko.util.ByteString

import scala.annotation.nowarn
import scala.collection.immutable

/**
 * Implementation of the gRPC (`application/grpc+proto`) protocol:
 *
 * Protocol:
 *  - Data frames are encoded to a stream of [[Chunk]] as per the gRPC specification
 *  - Trailer frames are encoded to [[LastChunk]], to be rendered into the underlying HTTP/2 transport
 */
object GrpcProtocolNative extends AbstractGrpcProtocol("grpc") {

  override protected def writer(codec: Codec) =
    AbstractGrpcProtocol.writer(this, codec, encodeFrame(codec, _), encodeDataToResponse(codec))

  override protected def reader(codec: Codec): GrpcProtocolReader =
    if (codec eq Identity)
      AbstractGrpcProtocol.reader(codec, decodeFrame).copy(decodeSingleFrame = decodeIdentitySingleFrame)
    else AbstractGrpcProtocol.reader(codec, decodeFrame)

  @inline
  private def decodeFrame(@nowarn("msg=is never used") frameType: Int, data: ByteString) = DataFrame(data)

  private def decodeIdentitySingleFrame(frame: ByteString): ByteString = {
    if (frame.length < AbstractGrpcProtocol.FrameHeaderSize) throw new MissingParameterException

    val frameType = frame(0)
    val length = frame.readIntBE(1)
    val available = frame.length - AbstractGrpcProtocol.FrameHeaderSize
    if (length > available) throw new MissingParameterException
    if (length < 0 || length < available)
      throw new IllegalStateException("Unexpected data")
    if ((frameType & 0x80) != 0) throw new IllegalStateException("Cannot read unknown frame")

    if ((frameType & 1) != 0)
      throw new io.grpc.StatusException(
        io.grpc.Status.INTERNAL.withDescription("Compressed-Flag bit is set, but a compression encoding is not specified"))
    frame.drop(AbstractGrpcProtocol.FrameHeaderSize)
  }

  @inline
  private def encodeFrame(codec: Codec, frame: Frame): ChunkStreamPart =
    frame match {
      case DataFrame(data) =>
        Chunk(AbstractGrpcProtocol.encodeFrameData(codec.compress(data), codec.isCompressed, isTrailer = false))
      case TrailerFrame(headers) => LastChunk(trailer = headers)
    }
  private def encodeDataToResponse(
      codec: Codec)(data: ByteString, headers: immutable.Seq[HttpHeader], trailer: Trailer): HttpResponse =
    new HttpResponse(
      status = StatusCodes.OK,
      headers = headers,
      entity = HttpEntity(contentType, encodeDataToFrameBytes(codec, data)),
      protocol = HttpProtocols.`HTTP/1.1`,
      attributes = Map.empty[AttributeKey[?], Any].updated(AttributeKeys.trailer, trailer))

  private def encodeDataToFrameBytes(codec: Codec, data: ByteString): ByteString =
    AbstractGrpcProtocol.encodeFrameData(codec.compress(data), codec.isCompressed, isTrailer = false)
}
