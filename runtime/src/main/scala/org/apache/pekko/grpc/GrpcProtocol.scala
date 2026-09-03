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

package org.apache.pekko.grpc

import org.apache.pekko
import org.apache.pekko.grpc.GrpcProtocol.DeferredDataFrame.DeferredDataWriter
import pekko.NotUsed
import pekko.annotation.InternalApi
import pekko.annotation.InternalStableApi
import pekko.grpc.GrpcProtocol.{ GrpcProtocolReader, GrpcProtocolWriter }
import pekko.grpc.internal.{
  AbstractGrpcProtocol,
  Codec,
  Codecs,
  GrpcProtocolNative,
  GrpcProtocolWeb,
  GrpcProtocolWebText
}
import pekko.http.javadsl.{ model => jmodel }
import pekko.http.scaladsl.model.{ ContentType, HttpHeader, HttpResponse, Trailer }
import pekko.http.scaladsl.model.HttpEntity.ChunkStreamPart
import pekko.stream.scaladsl.Flow
import pekko.util.ByteString

import scala.collection.immutable
import scala.util.Try

/**
 * A variant of the gRPC protocol - e.g. gRPC and gRPC-Web
 */
trait GrpcProtocol {

  /**
   * INTERNAL API
   *
   * The canonical media type to use for this protocol variant
   */
  @InternalApi
  private[grpc] val contentType: ContentType.Binary

  /**
   * INTERNAL API
   *
   * The set of media types that can identify this protocol variant (e.g. including an implicit +proto)
   */
  @InternalApi
  private[grpc] val mediaTypes: Set[jmodel.MediaType]

  /**
   * INTERNAL API
   *
   * Constructs a protocol writer for writing gRPC protocol frames for this variant
   * @param codec the compression codec to encode data frame bodies with.
   */
  @InternalStableApi
  def newWriter(codec: Codec): GrpcProtocolWriter

  /**
   * INTERNAL API
   *
   * Constructs a protocol reader for reading gRPC protocol frames for this variant.
   * @param codec the compression codec to decode data frame bodies with.
   * @param maxInboundMessageSize the maximum allowed inbound message size in bytes.
   */
  @InternalStableApi
  def newReader(codec: Codec, maxInboundMessageSize: Int = AbstractGrpcProtocol.DefaultMaxInboundMessageSize)
      : GrpcProtocolReader
}

/**
 * INTERNAL API
 *
 * Core definitions for gRPC protocols.
 */
@InternalApi
object GrpcProtocol {

  /** A frame in a logical gRPC protocol stream */
  sealed trait Frame

  /** An outbound data frame to be encoded in a gRPC protocol stream */
  sealed trait OutboundFrame extends Frame

  /** A frame received from a peer system. */
  sealed trait InboundFrame extends Frame

  /** A data (or message) frame in a gRPC protocol stream */
  case class DataFrame(data: ByteString) extends InboundFrame with OutboundFrame

  /** A trailer (status headers) frame in a gRPC protocol stream */
  case class TrailerFrame(trailers: List[HttpHeader]) extends InboundFrame with OutboundFrame

  /** An outbound write of a data frame, deferred to allow optimized write into a pre-allocated buffer */
  case class DeferredDataFrame[T](element: T, writer: DeferredDataWriter[T]) extends OutboundFrame {
    type Element = T

    /** Fallback to DataFrame style write, when optimized framed write is not possible. */
    def data: ByteString = {
      val data = new Array[Byte](writer.serializedSize(element))
      writer.serializeTo(element, data, 0)
      ByteString.fromArrayUnsafe(data)
    }

  }

  object DeferredDataFrame {
    trait DeferredDataWriter[T] {

      /**
       * Compute the size of the serialized form of the given element.
       */
      def serializedSize(t: T): Int

      /**
       * Serialize the given element into the given frame, starting at the given offset.
       * @param t the element to serialize.
       * @param frame a preallocated frame buffer, which will be at least of size offset + serializedSize(t)
       * @param offset the offset to place the serialized data of the element at.
       */
      def serializeTo(t: T, frame: Array[Byte], offset: Int): Unit
    }
  }

  /**
   * Implements the encoding of a stream of gRPC Frames into a physical/transport layer.
   *
   * This maps the logical gRPC frames into a stream of chunks that can be handled by the HTTP/2 or HTTP/1.1 transport layer.
   */
  case class GrpcProtocolWriter(
      /** The media type produced by this writer */
      contentType: ContentType,
      /** The compression codec to be used for data frame bodies */
      messageEncoding: Codec,
      /** Encodes a frame as a part in a chunk stream. */
      encodeFrame: OutboundFrame => ChunkStreamPart,
      /** A shortcut to encode a data frame directly into a Response */
      encodeDataToResponse: (OutboundFrame, immutable.Seq[HttpHeader], Trailer) => HttpResponse)

  /**
   * Implements the decoding of the gRPC framing from a physical/transport layer.
   */
  case class GrpcProtocolReader(
      /** The compression codec to be used for data frames */
      messageEncoding: Codec,
      decodeSingleFrame: ByteString => ByteString,
      /** A Flow of Frames over a stream of messages encoded in gRPC framing. */
      frameDecoder: Flow[ByteString, InboundFrame, NotUsed]) {

    /**
     * A Flow of Frames over a stream of messages encoded in gRPC framing that only
     * expects data frames, and produces the body of each data frame.
     * This flow will throw IllegalStateException if anything other than a data frame is encountered.
     */
    val dataFrameDecoder: Flow[ByteString, ByteString, NotUsed] = frameDecoder.map {
      case DataFrame(data) => data
      case _               => throw new IllegalStateException("Expected only Data frames in stream")
    }
  }

  /**
   * Detects which gRPC protocol variant is indicated in a request.
   * @return a [[GrpcProtocol]] matching the request mediatype if specified and known.
   */
  def detect(request: jmodel.HttpRequest): Option[GrpcProtocol] = detect(request.entity.getContentType.mediaType)

  /**
   * Detects which gRPC protocol variant is indicated by a mediatype.
   * @return a [[GrpcProtocol]] matching the request mediatype if known.
   */
  def detect(mediaType: jmodel.MediaType): Option[GrpcProtocol] = mediaType.subType match {
    // mainType is not checked. We assume it's the right one.
    case "grpc" | "grpc+proto"                   => Some(GrpcProtocolNative)
    case "grpc-web" | "grpc-web+proto"           => Some(GrpcProtocolWeb)
    case "grpc-web-text" | "grpc-web-text+proto" => Some(GrpcProtocolWebText)
    case _                                       => None
  }

  /**
   * Calculates the gRPC protocol encoding to use for an interaction with a gRPC client.
   *
   * @param request the client request to respond to.
   * @return the protocol reader for the request, and a protocol writer for the response.
   */
  def negotiate(request: jmodel.HttpRequest): Option[(Try[GrpcProtocolReader], GrpcProtocolWriter)] =
    negotiate(request, AbstractGrpcProtocol.DefaultMaxInboundMessageSize)

  /**
   * Calculates the gRPC protocol encoding to use for an interaction with a gRPC client.
   *
   * @param request the client request to respond to.
   * @param maxInboundMessageSize the maximum allowed inbound message size in bytes.
   * @return the protocol reader for the request, and a protocol writer for the response.
   */
  def negotiate(
      request: jmodel.HttpRequest,
      maxInboundMessageSize: Int): Option[(Try[GrpcProtocolReader], GrpcProtocolWriter)] =
    detect(request).map { variant =>
      (Codecs.detect(request).map(variant.newReader(_, maxInboundMessageSize)),
        variant.newWriter(Codecs.negotiate(request)))
    }

}
