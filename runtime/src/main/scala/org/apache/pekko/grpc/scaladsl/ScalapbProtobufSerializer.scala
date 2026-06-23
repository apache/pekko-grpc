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
import pekko.annotation.ApiMayChange
import pekko.grpc.ProtobufFrameSerializer
import pekko.grpc.internal.AbstractGrpcProtocol
import pekko.util.ByteString
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.CodedInputStream
import scalapb.{ GeneratedMessage, GeneratedMessageCompanion }

import java.io.InputStream

@ApiMayChange
class ScalapbProtobufSerializer[T <: GeneratedMessage](companion: GeneratedMessageCompanion[T])
    extends ProtobufFrameSerializer[T] {
  override def serialize(t: T): ByteString =
    ByteString.fromArrayUnsafe(t.toByteArray)
  override private[grpc] def serializedDataSize(t: T): Int = t.serializedSize
  override private[grpc] def serializeDataFrame(t: T): ByteString = {
    val dataLength = t.serializedSize
    val frame = new Array[Byte](AbstractGrpcProtocol.FrameHeaderSize + dataLength)
    AbstractGrpcProtocol.writeFrameHeader(frame, 0, dataLength, isCompressed = false, isTrailer = false)

    val output = CodedOutputStream.newInstance(frame, AbstractGrpcProtocol.FrameHeaderSize, dataLength)
    t.writeTo(output)
    output.checkNoSpaceLeft()

    ByteString.fromArrayUnsafe(frame)
  }
  override def deserialize(bytes: ByteString): T = {
    // Fast path: use byte array directly to avoid ByteBuffer wrapper allocation
    val bb = bytes.asByteBuffer
    if (bb.hasArray) {
      companion.parseFrom(CodedInputStream.newInstance(bb.array(), bb.arrayOffset(), bb.remaining()))
    } else {
      companion.parseFrom(CodedInputStream.newInstance(bb))
    }
  }
  override private[grpc] def deserialize(data: ByteString, offset: Int, length: Int): T = {
    // Offset-aware fast path: bypass ByteString.slice to avoid ByteString1 + ByteBuffer wrappers
    val bb = data.asByteBuffer
    if (bb.hasArray) {
      companion.parseFrom(CodedInputStream.newInstance(bb.array(), bb.arrayOffset() + offset, length))
    } else {
      // Fallback for non-array-backed ByteStrings (rare in practice)
      super.deserialize(data, offset, length)
    }
  }
  override def deserialize(data: InputStream): T =
    companion.parseFrom(data)
}
