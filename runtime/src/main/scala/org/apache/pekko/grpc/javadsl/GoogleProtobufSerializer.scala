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

package org.apache.pekko.grpc.javadsl

import org.apache.pekko
import pekko.annotation.ApiMayChange
import pekko.grpc.ProtobufSerializer
import pekko.util.ByteString
import com.google.protobuf.Parser

import java.io.InputStream

@ApiMayChange
class GoogleProtobufSerializer[T <: com.google.protobuf.Message](parser: Parser[T]) extends ProtobufSerializer[T] {

  override def serialize(t: T): ByteString =
    ByteString.fromArrayUnsafe(t.toByteArray)
  override def deserialize(bytes: ByteString): T = {
    val inputStream = bytes.asInputStream
    try parser.parseFrom(inputStream)
    finally inputStream.close()
  }
  override def deserialize(data: InputStream): T =
    parser.parseFrom(data)
}
