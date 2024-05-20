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

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }
import java.util.zip.{ GZIPInputStream, GZIPOutputStream }

import org.apache.pekko.util.ByteString

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
    val gzis = new GZIPInputStream(ByteStringInputStream(compressed))

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

  override def uncompress(compressedBitSet: Boolean, bytes: ByteString): ByteString =
    if (compressedBitSet) uncompress(bytes)
    else bytes
}
