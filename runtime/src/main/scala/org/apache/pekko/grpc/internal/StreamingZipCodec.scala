/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.grpc.internal

import java.io.{ InputStream, OutputStream }

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.util.ByteString
import io.grpc.{ Status, StatusException }

import scala.annotation.nowarn

/**
 * INTERNAL API
 *
 * A codec backed by `java.util.zip` streams, which enforces the maximum decompressed size while
 * decompressing rather than after.
 */
@InternalApi
private[grpc] abstract class StreamingZipCodec extends Codec {

  protected def compressingStream(out: OutputStream): OutputStream

  protected def decompressingStream(in: InputStream): InputStream

  override def compress(uncompressed: ByteString): ByteString = {
    val bsos = new ByteStringOutputStream(uncompressed.size)
    val out = compressingStream(bsos)
    try out.write(uncompressed.toArrayUnsafe())
    finally out.close()
    bsos.takeByteString()
  }

  @deprecated("Use uncompress(bytes, maxDecompressedSize), which bounds the decompressed size", "2.0.0")
  override def uncompress(compressed: ByteString): ByteString = uncompress(compressed, Int.MaxValue)

  /**
   * Decompress with a maximum decompressed size limit.
   * Checks cumulative output size during decompression to fail fast
   * before allocating excessive memory.
   */
  override def uncompress(compressed: ByteString, maxDecompressedSize: Int): ByteString = {
    val limit = maxDecompressedSize.toLong
    // clamp: maxDecompressedSize is validated to be positive by the settings classes, but this
    // method is also reachable with a hand-constructed limit, and a negative initial size would
    // make ByteStringOutputStream throw IllegalArgumentException rather than a gRPC status.
    val initialSize = Math.max(0L, Math.min(compressed.size.toLong, limit)).toInt
    val in = decompressingStream(compressed.asInputStream)
    val bsos = new ByteStringOutputStream(initialSize)
    val buffer = new Array[Byte](32 * 1024)
    // Long, so that a limit close to Int.MaxValue cannot be passed by an overflowing counter
    var totalBytes = 0L
    try {
      var read = in.read(buffer)
      while (read != -1) {
        totalBytes += read
        if (totalBytes > limit)
          throw new StatusException(
            Status.RESOURCE_EXHAUSTED.withDescription(
              s"Decompressed message size exceeds maximum allowed $maxDecompressedSize bytes"))
        bsos.write(buffer, 0, read)
        read = in.read(buffer)
      }
    } finally in.close()
    bsos.takeByteString()
  }

  @deprecated(
    "Use uncompress(compressedBitSet, bytes, maxDecompressedSize), which bounds the decompressed size",
    "2.0.0")
  @nowarn("cat=deprecation")
  override def uncompress(compressedBitSet: Boolean, bytes: ByteString): ByteString =
    if (compressedBitSet) uncompress(bytes)
    else bytes
}
