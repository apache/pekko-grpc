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

import java.io.ByteArrayOutputStream
import java.util.zip.{ GZIPInputStream, GZIPOutputStream }

import org.apache.pekko.util.ByteString
import io.grpc.{ Status, StatusException }

object Gzip extends Codec {
  override val name: String = "gzip"

  override def compress(uncompressed: ByteString): ByteString = {
    val baos = new ByteArrayOutputStream(uncompressed.size)
    val gzos = new GZIPOutputStream(baos)
    try gzos.write(uncompressed.toArrayUnsafe())
    finally gzos.close()
    ByteString.fromArrayUnsafe(baos.toByteArray)
  }

  override def uncompress(compressed: ByteString): ByteString = {
    val gzis = new GZIPInputStream(compressed.asInputStream)

    val baos = new ByteArrayOutputStream(compressed.size)
    val buffer = new Array[Byte](32 * 1024)
    try {
      var read = gzis.read(buffer)
      while (read != -1) {
        baos.write(buffer, 0, read)
        read = gzis.read(buffer)
      }
    } finally gzis.close()
    ByteString.fromArrayUnsafe(baos.toByteArray)
  }

  /**
   * Decompress with a maximum decompressed size limit.
   * Checks cumulative output size during decompression to fail fast
   * before allocating excessive memory.
   */
  override def uncompress(compressed: ByteString, maxDecompressedSize: Int): ByteString = {
    val limit = maxDecompressedSize.toLong
    // clamp: maxDecompressedSize is validated to be positive by the settings classes, but this
    // method is also reachable with a hand-constructed limit, and a negative initial size would
    // make ByteArrayOutputStream throw IllegalArgumentException rather than a gRPC status.
    val initialSize = Math.max(0L, Math.min(compressed.size.toLong, limit)).toInt
    val gzis = new GZIPInputStream(compressed.asInputStream)
    val baos = new ByteArrayOutputStream(initialSize)
    val buffer = new Array[Byte](32 * 1024)
    // Long, so that a limit close to Int.MaxValue cannot be passed by an overflowing counter
    var totalBytes = 0L
    try {
      var read = gzis.read(buffer)
      while (read != -1) {
        totalBytes += read
        if (totalBytes > limit)
          throw new StatusException(
            Status.RESOURCE_EXHAUSTED.withDescription(
              s"Decompressed message size exceeds maximum allowed $maxDecompressedSize bytes"))
        baos.write(buffer, 0, read)
        read = gzis.read(buffer)
      }
    } finally gzis.close()
    ByteString.fromArrayUnsafe(baos.toByteArray)
  }

  override def uncompress(compressedBitSet: Boolean, bytes: ByteString): ByteString =
    if (compressedBitSet) uncompress(bytes)
    else bytes
}
