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

package org.apache.pekko.grpc.javadsl

import com.google.protobuf.{ Any => ProtobufAny, ByteString }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GoogleProtobufSerializerSpec extends AnyWordSpec with Matchers {
  "Google protobuf serializer" should {
    "successfully serialize and deserialize a protobuf Any object" in {
      val anySerializer = new GoogleProtobufSerializer(ProtobufAny.parser())

      val obj = ProtobufAny.newBuilder().setTypeUrl("asdf").setValue(ByteString.copyFromUtf8("ASDF")).build()
      val serialized = anySerializer.serialize(obj)
      val deserialized = anySerializer.deserialize(serialized)
      deserialized should be(obj)
    }
  }
}
