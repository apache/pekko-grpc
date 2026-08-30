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

import scala.annotation.nowarn

abstract class Codec {
  val name: String

  def compress(bytes: ByteString): ByteString

  /**
   * Decompress the given bytes with no bound on the output size.
   *
   * A compressed frame can inflate to arbitrarily many bytes, so a caller that does not
   * impose a limit is exposed to a decompression bomb. Prefer
   * `uncompress(bytes, maxDecompressedSize)`, which fails with `RESOURCE_EXHAUSTED`
   * instead of allocating without bound.
   */
  @deprecated("Use uncompress(bytes, maxDecompressedSize), which bounds the decompressed size", "2.0.0")
  def uncompress(bytes: ByteString): ByteString

  /**
   * Process the given frame bytes, uncompress if the compression bit is set. Identity
   * codec will fail with a `io.grpc.StatusException` if the compressedBit is set.
   *
   * Places no bound on the decompressed size; prefer
   * `uncompress(compressedBitSet, bytes, maxDecompressedSize)`.
   */
  @deprecated(
    "Use uncompress(compressedBitSet, bytes, maxDecompressedSize), which bounds the decompressed size",
    "2.0.0")
  def uncompress(compressedBitSet: Boolean, bytes: ByteString): ByteString

  /**
   * Decompress the given bytes, enforcing a maximum decompressed size.
   * Throws a `StatusException` with `RESOURCE_EXHAUSTED` if the decompressed
   * output exceeds `maxDecompressedSize`.
   *
   * This default implementation decompresses in full and checks afterwards, so it bounds
   * what a caller receives but not what is allocated along the way. Codecs that can enforce
   * the limit while decompressing should override it; `Gzip` does.
   *
   * @param bytes the compressed bytes
   * @param maxDecompressedSize the maximum allowed decompressed size in bytes
   */
  @nowarn("cat=deprecation")
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
   * Delegates to `uncompress(bytes, maxDecompressedSize)` so that codecs which can enforce
   * the limit while decompressing (rather than after) get the chance to fail fast.
   *
   * @param compressedBitSet whether the compression bit is set
   * @param bytes the frame bytes
   * @param maxDecompressedSize the maximum allowed decompressed size in bytes
   */
  def uncompress(compressedBitSet: Boolean, bytes: ByteString, maxDecompressedSize: Int): ByteString =
    if (compressedBitSet) uncompress(bytes, maxDecompressedSize)
    else if (bytes.length > maxDecompressedSize)
      throw new StatusException(
        Status.RESOURCE_EXHAUSTED.withDescription(
          s"Message size ${bytes.length} exceeds maximum allowed $maxDecompressedSize bytes"))
    else bytes

  def isCompressed: Boolean = this != Identity
}
