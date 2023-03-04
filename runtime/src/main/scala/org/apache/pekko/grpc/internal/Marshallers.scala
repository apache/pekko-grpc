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

package org.apache.pekko.grpc.internal

import java.io.{ ByteArrayInputStream, InputStream }

import io.grpc.KnownLength
import org.apache.pekko
import pekko.annotation.InternalStableApi
import pekko.grpc.ProtobufSerializer

/**
 * INTERNAL API
 */
@InternalStableApi
abstract class BaseMarshaller[T](val protobufSerializer: ProtobufSerializer[T])
    extends io.grpc.MethodDescriptor.Marshaller[T]
    with WithProtobufSerializer[T] {
  override def parse(stream: InputStream): T =
    protobufSerializer.deserialize(stream)
}

/**
 * INTERNAL API
 */
@InternalStableApi
final class Marshaller[T <: scalapb.GeneratedMessage](protobufSerializer: ProtobufSerializer[T])
    extends BaseMarshaller[T](protobufSerializer) {
  override def parse(stream: InputStream): T = super.parse(stream)
  override def stream(value: T): InputStream =
    new ByteArrayInputStream(value.toByteArray) with KnownLength
}

/**
 * INTERNAL API
 */
@InternalStableApi
class ProtoMarshaller[T <: com.google.protobuf.Message](protobufSerializer: ProtobufSerializer[T])
    extends BaseMarshaller[T](protobufSerializer) {
  override def parse(stream: InputStream): T = super.parse(stream)
  override def stream(value: T): InputStream =
    new ByteArrayInputStream(value.toByteArray) with KnownLength
}
