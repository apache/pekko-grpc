/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.scaladsl

import org.apache.pekko
import pekko.annotation.ApiMayChange
import pekko.grpc.ProtobufSerializer
import pekko.util.ByteString
import com.google.protobuf.CodedInputStream
import scalapb.{ GeneratedMessage, GeneratedMessageCompanion }

import java.io.InputStream

@ApiMayChange
class ScalapbProtobufSerializer[T <: GeneratedMessage](companion: GeneratedMessageCompanion[T])
    extends ProtobufSerializer[T] {
  override def serialize(t: T): ByteString =
    ByteString.fromArrayUnsafe(t.toByteArray)
  override def deserialize(bytes: ByteString): T =
    companion.parseFrom(CodedInputStream.newInstance(bytes.asByteBuffer))
  override def deserialize(data: InputStream): T =
    companion.parseFrom(data)
}
