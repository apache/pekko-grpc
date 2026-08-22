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

import org.apache.pekko.util.ByteString
import io.grpc.{ Status, StatusException }

abstract class Codec {
  val name: String

  def compress(bytes: ByteString): ByteString
  def uncompress(bytes: ByteString): ByteString

  /**
   * Process the given frame bytes, uncompress if the compression bit is set. Identity
   * codec will fail with a `io.grpc.StatusException` if the compressedBit is set.
   */
  def uncompress(compressedBitSet: Boolean, bytes: ByteString): ByteString

  /**
   * Decompress the given bytes, enforcing a maximum decompressed size.
   * Throws a `StatusException` with `RESOURCE_EXHAUSTED` if the decompressed
   * output exceeds `maxDecompressedSize`.
   *
   * @param bytes the compressed bytes
   * @param maxDecompressedSize the maximum allowed decompressed size in bytes
   */
  def uncompress(bytes: ByteString, maxDecompressedSize: Int): ByteString = {
    val result = uncompress(bytes)
    if (result.length > maxDecompressedSize)
      throw new StatusException(
        Status.RESOURCE_EXHAUSTED.withDescription(
          s"Decompressed message size ${result.length} exceeds maximum allowed $maxDecompressedSize"))
    result
  }

  /**
   * Process the given frame bytes, uncompress if the compression bit is set,
   * enforcing a maximum decompressed size.
   *
   * @param compressedBitSet whether the compression bit is set
   * @param bytes the frame bytes
   * @param maxDecompressedSize the maximum allowed decompressed size in bytes
   */
  def uncompress(compressedBitSet: Boolean, bytes: ByteString, maxDecompressedSize: Int): ByteString = {
    val result = uncompress(compressedBitSet, bytes)
    if (result.length > maxDecompressedSize)
      throw new StatusException(
        Status.RESOURCE_EXHAUSTED.withDescription(
          s"Decompressed message size ${result.length} exceeds maximum allowed $maxDecompressedSize"))
    result
  }

  def isCompressed: Boolean = this != Identity
}
