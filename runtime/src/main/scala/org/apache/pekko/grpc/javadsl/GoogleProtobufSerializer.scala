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
import pekko.grpc.internal.ByteStringInputStream
import pekko.util.ByteString
import com.google.protobuf.Parser

import java.io.InputStream

@ApiMayChange
class GoogleProtobufSerializer[T <: com.google.protobuf.Message](parser: Parser[T]) extends ProtobufSerializer[T] {

  @deprecated("Kept for binary compatibility, use the main constructor instead", since = "akka-grpc 1.1.2")
  def this(clazz: Class[T]) =
    this(clazz.getMethod("parser").invoke(clazz).asInstanceOf[Parser[T]])

  override def serialize(t: T): ByteString =
    ByteString.fromArrayUnsafe(t.toByteArray)
  override def deserialize(bytes: ByteString): T =
    parser.parseFrom(ByteStringInputStream(bytes))
  override def deserialize(data: InputStream): T =
    parser.parseFrom(data)
}
