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

package org.apache.pekko.grpc

import org.apache.pekko
import pekko.grpc.internal.ByteStringUtils
import pekko.util.ByteString

import java.io.InputStream

trait ProtobufSerializer[T] {
  def serialize(t: T): ByteString
  def deserialize(bytes: ByteString): T
  def deserialize(stream: InputStream): T = deserialize(ByteStringUtils.fromInputStream(stream))
}

private[grpc] trait ProtobufFrameSerializer[T] extends ProtobufSerializer[T] {
  private[grpc] def serializeDataFrame(t: T): ByteString

  /**
   * Deserialize a protobuf message from a ByteString starting at the given offset.
   * Avoids ByteString.slice allocation by passing the offset directly to the parser.
   * Default implementation falls back to slice + deserialize.
   */
  private[grpc] def deserialize(data: ByteString, offset: Int, length: Int): T =
    deserialize(data.slice(offset, offset + length))

  /**
   * Returns the serialized size of the message without actually serializing it.
   * Used by adaptive compression to decide whether to compress small messages.
   *
   * Implementations should override this with an efficient method (e.g.
   * `GeneratedMessage.serializedSize` for ScalaPB or `Message.getSerializedSize`
   * for Google protobuf) to avoid the overhead of full serialization.
   */
  private[grpc] def serializedDataSize(t: T): Int = serialize(t).length
}
