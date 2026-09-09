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
import pekko.grpc.GrpcProtocol
import pekko.grpc.GrpcProtocol.{ GrpcProtocolReader, GrpcProtocolWriter, InboundFrame, OutboundFrame }
import pekko.grpc.GrpcProtocol.DeferredDataFrame.DeferredDataWriter
import pekko.http.javadsl.{ model => jmodel }
import pekko.http.scaladsl.model.HttpEntity.ChunkStreamPart
import pekko.http.scaladsl.model.{ ContentType, HttpHeader, HttpResponse, MediaType, Trailer }
import pekko.stream.Attributes
import pekko.stream.impl.io.ByteStringParser
import pekko.stream.impl.io.ByteStringParser.{ ByteReader, ParseResult, ParseStep }
import pekko.stream.scaladsl.Flow
import pekko.stream.stage.GraphStageLogic
import pekko.util.ByteString
import io.grpc.{ Status, StatusException }

import scala.collection.immutable

abstract class AbstractGrpcProtocol(subType: String) extends GrpcProtocol {

  override val contentType: ContentType.Binary =
    MediaType.applicationBinary(s"$subType+proto", MediaType.Compressible).toContentType

  override val mediaTypes: Set[jmodel.MediaType] =
    Set(contentType.mediaType, MediaType.applicationBinary(subType, MediaType.Compressible))

  private lazy val knownWriters = Codecs.supportedCodecs.map(c => c -> writer(c)).toMap.withDefault(writer)

  /**
   * Readers for the default message size limit. `newReader` is called once per inbound request, so
   * the common case (no per-service override) is served from this cache rather than rebuilding the
   * reader, its framing stage and the surrounding flows every time.
   */
  private lazy val knownReaders =
    Codecs.supportedCodecs
      .map(c => c -> reader(c, AbstractGrpcProtocol.DefaultMaxInboundMessageSize))
      .toMap
      .withDefault(reader(_, AbstractGrpcProtocol.DefaultMaxInboundMessageSize))

  /**
   * Obtains a writer for this protocol:
   * @param codec the compression codec to apply to data frame contents.
   */
  override def newWriter(codec: Codec): GrpcProtocolWriter = knownWriters(codec)

  /**
   * Obtains a reader for this protocol.
   *
   * @param codec the codec to use for compressed frames.
   * @param maxInboundMessageSize the maximum allowed inbound message size in bytes.
   */
  override def newReader(
      codec: Codec,
      maxInboundMessageSize: Int = AbstractGrpcProtocol.DefaultMaxInboundMessageSize): GrpcProtocolReader =
    if (maxInboundMessageSize == AbstractGrpcProtocol.DefaultMaxInboundMessageSize) knownReaders(codec)
    else reader(codec, maxInboundMessageSize)

  protected def writer(codec: Codec): GrpcProtocolWriter

  protected def reader(codec: Codec, maxInboundMessageSize: Int): GrpcProtocolReader

}
object AbstractGrpcProtocol {

  /** Field marker to signal the start of an uncompressed frame */
  private val notCompressed: ByteString = ByteString(0)

  /** Field marker to signal the start of an frame compressed according to the Message-Encoding from the header */
  private val compressed: ByteString = ByteString(1)

  def fieldType(codec: Codec) = if (codec == Identity) notCompressed else compressed

  private[grpc] final val FrameHeaderSize = 5

  private[grpc] def writeFrameHeader(
      frame: Array[Byte],
      offset: Int,
      dataLength: Int,
      isCompressed: Boolean,
      isTrailer: Boolean): Unit = {
    val flags =
      (if (isCompressed) 1 else 0) | (if (isTrailer) 0x80 else 0)

    frame(offset) = flags.toByte
    frame(offset + 1) = (dataLength >>> 24).toByte
    frame(offset + 2) = (dataLength >>> 16).toByte
    frame(offset + 3) = (dataLength >>> 8).toByte
    frame(offset + 4) = dataLength.toByte
  }

  /**
   * Adjusts the compressibility of a content type to suit a message encoding.
   * @param contentType the content type for the gRPC protocol.
   * @param codec the message encoding being used to encode objects.
   * @return the provided content type, with the compressibility adapted to reflect whether HTTP transport level compression should be used.
   */
  private def adjustCompressibility(contentType: ContentType.Binary, codec: Codec): ContentType.Binary =
    contentType.mediaType
      .withComp(codec match {
        case Identity => MediaType.Compressible
        case _        => MediaType.NotCompressible
      })
      .toContentType

  def encodeFrameData[T](element: T, elementDataWriter: DeferredDataWriter[T], isCompressed: Boolean,
      isTrailer: Boolean): ByteString = {
    val dataLength = elementDataWriter.serializedSize(element)
    val frame = new Array[Byte](AbstractGrpcProtocol.FrameHeaderSize + dataLength)
    writeFrameHeader(frame, 0, dataLength, isCompressed, isTrailer)
    elementDataWriter.serializeTo(element, frame, AbstractGrpcProtocol.FrameHeaderSize)
    ByteString.fromArrayUnsafe(frame)
  }

  def encodeFrameData(data: ByteString, isCompressed: Boolean, isTrailer: Boolean): ByteString = {
    val header = new Array[Byte](FrameHeaderSize)
    writeFrameHeader(header, 0, data.length, isCompressed, isTrailer)
    ByteString.fromArrayUnsafe(header, 0, FrameHeaderSize) ++ data
  }

  def writer(
      protocol: GrpcProtocol,
      codec: Codec,
      encodeFrame: OutboundFrame => ChunkStreamPart,
      encodeDataToResponse: (OutboundFrame, immutable.Seq[HttpHeader], Trailer) => HttpResponse): GrpcProtocolWriter =
    GrpcProtocolWriter(
      adjustCompressibility(protocol.contentType, codec),
      codec,
      encodeFrame,
      encodeDataToResponse)

  /**
   * The default maximum inbound message size (4 MiB), matching grpc-java's default.
   */
  val DefaultMaxInboundMessageSize: Int = 4 * 1024 * 1024

  def reader(
      codec: Codec,
      decodeFrame: (Int, ByteString) => InboundFrame,
      preDecodeStrict: ByteString => ByteString = null,
      preDecodeFlow: Flow[ByteString, ByteString, NotUsed] = null,
      maxInboundMessageSize: Int = DefaultMaxInboundMessageSize): GrpcProtocolReader = {
    val strictAdapter: ByteString => ByteString = if (preDecodeStrict eq null) identity else preDecodeStrict
    val adapter: Flow[ByteString, InboundFrame, NotUsed] => Flow[ByteString, InboundFrame, NotUsed] =
      if (preDecodeFlow eq null) identity
      else x => Flow[ByteString].via(preDecodeFlow).via(x)

    // strict decoder
    def decoder(bs: ByteString): ByteString =
      try {
        val reader = new ByteReader(strictAdapter(bs))
        val frameType = reader.readByte()
        val length = reader.readIntBE()
        if (length < 0)
          throw new StatusException(Status.INTERNAL.withDescription(s"Frame length must not be negative, was $length"))
        if (length > maxInboundMessageSize)
          throw new StatusException(
            Status.RESOURCE_EXHAUSTED.withDescription(
              s"Frame length $length exceeds maximum inbound message size $maxInboundMessageSize"))
        val data = reader.take(length)
        if (reader.hasRemaining) throw new IllegalStateException("Unexpected data")
        if ((frameType & 0x80) == 0) codec.uncompress((frameType & 1) == 1, data, maxInboundMessageSize)
        else throw new IllegalStateException("Cannot read unknown frame")
      } catch { case ByteStringParser.NeedMoreData => throw new MissingParameterException }

    GrpcProtocolReader(
      codec,
      decoder,
      adapter(Flow.fromGraph(new GrpcFramingDecoderStage(codec, decodeFrame, maxInboundMessageSize))))
  }

  class GrpcFramingDecoderStage(codec: Codec, deframe: (Int, ByteString) => InboundFrame, maxInboundMessageSize: Int)
      extends ByteStringParser[InboundFrame] {
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new ParsingLogic {
        startWith(ReadFrameHeader)

        trait Step extends ParseStep[InboundFrame]

        // handle explicitly to avoid noisy log: a peer sending oversized or malformed frame
        // headers should not cost us a stack trace per attempt
        private def failWith(status: Status): ParseResult[InboundFrame] = {
          failStage(new StatusException(status))
          ParseResult(None, Failed)
        }

        object ReadFrameHeader extends Step {
          override def parse(reader: ByteReader): ParseResult[InboundFrame] = {
            val frameType = reader.readByte()
            val length = reader.readIntBE()

            if (length < 0)
              failWith(Status.INTERNAL.withDescription(s"Frame length must not be negative, was $length"))
            else if (length > maxInboundMessageSize)
              failWith(
                Status.RESOURCE_EXHAUSTED.withDescription(
                  s"Frame length $length exceeds maximum inbound message size $maxInboundMessageSize"))
            else if (length == 0) ParseResult(Some(deframe(frameType, ByteString.empty)), ReadFrameHeader)
            else ParseResult(None, ReadFrame(frameType, length), acceptUpstreamFinish = false)
          }
        }

        sealed case class ReadFrame(frameType: Int, length: Int) extends Step {
          private val compression = (frameType & 0x01) == 1

          override def parse(reader: ByteReader): ParseResult[InboundFrame] =
            try ParseResult(
                Some(deframe(frameType, codec.uncompress(compression, reader.take(length), maxInboundMessageSize))),
                ReadFrameHeader)
            catch {
              case s: StatusException =>
                failStage(s) // handle explicitly to avoid noisy log
                ParseResult(None, Failed)
            }
        }

        case object Failed extends Step {
          override def parse(reader: ByteReader): ParseResult[InboundFrame] = ParseResult(None, Failed)
        }
      }
  }
}
