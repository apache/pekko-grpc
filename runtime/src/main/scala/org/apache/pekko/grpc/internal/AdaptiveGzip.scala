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

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.util.ByteString

/**
 * Adaptive gzip codec that only compresses messages above a size threshold.
 *
 * For small messages, the CPU overhead of gzip compression exceeds the bandwidth
 * savings. This codec checks the message size before compressing and passes through
 * uncompressed data for small messages, while still advertising "gzip" as the
 * encoding to the client. The gRPC per-frame compression flag (bit 0 of the
 * 5-byte frame header) tells the client whether each individual frame is compressed.
 *
 * @param compressionThreshold minimum message size in bytes to trigger compression
 */
@InternalApi
private[grpc] class AdaptiveGzip(val compressionThreshold: Int) extends Codec {
  override val name: String = "gzip"

  override def compress(uncompressed: ByteString): ByteString = {
    if (uncompressed.size < compressionThreshold) uncompressed
    else {
      val baos = new ByteArrayOutputStream(uncompressed.size)
      val gzos = new GZIPOutputStream(baos)
      try gzos.write(uncompressed.toArrayUnsafe())
      finally gzos.close()
      ByteString.fromArrayUnsafe(baos.toByteArray)
    }
  }

  override def uncompress(compressed: ByteString): ByteString = Gzip.uncompress(compressed)

  override def uncompress(compressedBitSet: Boolean, bytes: ByteString): ByteString =
    Gzip.uncompress(compressedBitSet, bytes)

  override def isCompressed: Boolean = true

  override def compressWithFlag(bytes: ByteString): (ByteString, Boolean) = {
    if (bytes.size < compressionThreshold) (bytes, false)
    else {
      val baos = new ByteArrayOutputStream(bytes.size)
      val gzos = new GZIPOutputStream(baos)
      try gzos.write(bytes.toArrayUnsafe())
      finally gzos.close()
      (ByteString.fromArrayUnsafe(baos.toByteArray), true)
    }
  }
}

@InternalApi
private[grpc] object AdaptiveGzip {
  val DefaultCompressionThreshold: Int = 1024

  private val DefaultInstance: AdaptiveGzip = new AdaptiveGzip(DefaultCompressionThreshold)

  def apply(threshold: Int = DefaultCompressionThreshold): AdaptiveGzip =
    if (threshold == DefaultCompressionThreshold) DefaultInstance
    else new AdaptiveGzip(threshold)
}
