/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.scaladsl

import com.google.protobuf.any.{ Any => ScalapbAny }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import com.google.protobuf.ByteString

class ScalapbProtobufSerializerSpec extends AnyWordSpec with Matchers {
  "Google protobuf serializer" should {
    "successfully serialize and deserialize a protobuf Any object" in {
      val anySerializer = new ScalapbProtobufSerializer(ScalapbAny)

      val obj = ScalapbAny("asdf", ByteString.copyFromUtf8("ASDF"))
      val serialized = anySerializer.serialize(obj)
      val deserialized = anySerializer.deserialize(serialized)
      deserialized should be(obj)
    }
  }
}
