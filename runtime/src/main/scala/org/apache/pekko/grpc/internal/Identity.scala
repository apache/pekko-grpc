/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.internal

import org.apache.pekko.util.ByteString
import io.grpc.{ Status, StatusException }

object Identity extends Codec {
  override val name = "identity"

  override def compress(bytes: ByteString): ByteString = bytes

  override def uncompress(bytes: ByteString): ByteString = bytes

  override def uncompress(compressedBitSet: Boolean, bytes: ByteString): ByteString =
    if (compressedBitSet)
      throw new StatusException(
        Status.INTERNAL.withDescription("Compressed-Flag bit is set, but a compression encoding is not specified"))
    else bytes
}
