/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.grpc.javadsl

import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration._

import com.google.protobuf.{ Any => ProtobufAny, ByteString => ProtobufByteString }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.grpc.internal.{ AbstractGrpcProtocol, GrpcProtocolNative, Identity }
import pekko.http.scaladsl.model.HttpEntity
import pekko.stream.SystemMaterializer

class GrpcMarshallingSpec extends AnyWordSpec with Matchers {
  "The javadsl GrpcMarshalling" should {
    "unmarshal a strict unary entity" in {
      val system = ActorSystem("GrpcMarshallingSpec")
      try {
        val mat = SystemMaterializer(system).materializer
        val serializer = new GoogleProtobufSerializer(ProtobufAny.parser())
        val message =
          ProtobufAny.newBuilder().setTypeUrl("benchmark").setValue(ProtobufByteString.copyFromUtf8("payload")).build()
        val entity =
          HttpEntity.Strict(
            GrpcProtocolNative.contentType,
            AbstractGrpcProtocol.encodeFrameData(serializer.serialize(message), isCompressed = false,
              isTrailer = false))

        val result =
          GrpcMarshalling
            .unmarshal(entity, serializer, mat, GrpcProtocolNative.newReader(Identity))
            .toCompletableFuture
            .get(10, TimeUnit.SECONDS)

        result should be(message)
      } finally {
        Await.result(system.terminate(), 10.seconds)
      }
    }
  }
}
