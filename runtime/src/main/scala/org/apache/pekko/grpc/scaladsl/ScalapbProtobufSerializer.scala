/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.scaladsl

import org.apache.pekko.annotation.ApiMayChange
import org.apache.pekko.grpc.ProtobufSerializer
import org.apache.pekko.util.ByteString
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
