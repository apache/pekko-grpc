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

abstract class Codec {
  val name: String

  def compress(bytes: ByteString): ByteString
  def uncompress(bytes: ByteString): ByteString

  /**
   * Process the given frame bytes, uncompress if the compression bit is set. Identity
   * codec will fail with a `io.grpc.StatusException` if the compressedBit is set.
   */
  def uncompress(compressedBitSet: Boolean, bytes: ByteString): ByteString

  def isCompressed: Boolean = this != Identity

  /**
   * Compress data and report whether compression was actually applied.
   * Returns a tuple of (compressedData, wasCompressed) where wasCompressed
   * indicates whether the data should be marked with the compression flag
   * in the gRPC frame header. Default implementation uses compress() + isCompressed.
   * Adaptive codecs override this to signal per-frame compression decisions.
   */
  def compressWithFlag(bytes: ByteString): (ByteString, Boolean) =
    (compress(bytes), isCompressed)
}
