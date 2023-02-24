/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc

import org.apache.pekko.grpc.internal.ByteStringUtils
import org.apache.pekko.util.ByteString

import java.io.InputStream

trait ProtobufSerializer[T] {
  def serialize(t: T): ByteString
  def deserialize(bytes: ByteString): T
  def deserialize(stream: InputStream): T = deserialize(ByteStringUtils.fromInputStream(stream))
}
