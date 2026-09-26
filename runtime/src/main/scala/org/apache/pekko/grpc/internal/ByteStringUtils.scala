/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.internal

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.util.ByteString
import io.grpc.KnownLength

import java.io.{ ByteArrayOutputStream, InputStream }

@InternalApi
private[grpc] object ByteStringUtils {
  def fromInputStream(stream: InputStream): ByteString = {
    val buffer =
      new Array[Byte](stream match {
        case k: KnownLength => math.max(0, k.available()) // No need to oversize this if we already know the size
        case _              => 32 * 1024
      })

    // Blocking calls underneath...
    // we can't avoid it for the moment because we are relying on the Netty's Channel API
    val initialBytes = stream.read(buffer, 0, buffer.length)
    val nextByte = if (initialBytes < 0) -1 else stream.read() // Test for EOF

    if (nextByte == -1)
      toByteStringUnsafe(buffer, initialBytes)
    else {
      val bsos = new ByteStringOutputStream(buffer.length * 2) // To avoid immediate resize
      bsos.write(buffer, 0, initialBytes)
      bsos.write(nextByte)

      var bytesRead = stream.read(buffer)
      while (bytesRead >= 0) {
        bsos.write(buffer, 0, bytesRead)
        bytesRead = stream.read(buffer)
      }

      bsos.toByteStringUnsafe
    }
  }

  def toByteStringUnsafe(buf: Array[Byte], count: Int): ByteString =
    if (count < 1)
      ByteString.empty
    else if (count > (buf.length >> 1))
      // Most of the buffer is used — reuse it to avoid a copy
      ByteString.fromArrayUnsafe(buf, 0, count)
    else
      // Small read from a large buffer — copy to right-size so the rest can be GC'd
      ByteString.fromArray(buf, 0, count)
}

/**
 * OutputStream to ByteString adapter, avoiding copying of the buffered data where possible.
 */
private class ByteStringOutputStream(capacity: Int) extends ByteArrayOutputStream(capacity) {

  /**
   * Wraps contents of the buffer in a ByteString.
   *
   * This can wrap an unsafe reference to the internal buffer of this output stream.
   * The caller must ensure that the output stream is not modified after this method is called.
   */
  def toByteStringUnsafe: ByteString = ByteStringUtils.toByteStringUnsafe(buf, count)
}
