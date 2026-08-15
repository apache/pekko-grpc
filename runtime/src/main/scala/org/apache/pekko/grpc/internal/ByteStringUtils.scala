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

    if (nextByte == -1) {
      if (initialBytes < 1) ByteString.empty // EOF immediately
      else if (initialBytes > (buffer.length >> 1))
        // Most of the buffer is used — reuse it to avoid a copy
        ByteString.fromArrayUnsafe(buffer, 0, initialBytes)
      else
        // Small read from a large buffer — copy to right-size so the rest can be GC'd
        ByteString.fromArray(buffer, 0, initialBytes)
    } else {
      val baos = new ByteArrayOutputStream(buffer.length * 2) // To avoid immediate resize
      baos.write(buffer, 0, initialBytes)
      baos.write(nextByte)

      var bytesRead = stream.read(buffer)
      while (bytesRead >= 0) {
        baos.write(buffer, 0, bytesRead)
        bytesRead = stream.read(buffer)
      }

      ByteString.fromArrayUnsafe(baos.toByteArray)
    }
  }
}
