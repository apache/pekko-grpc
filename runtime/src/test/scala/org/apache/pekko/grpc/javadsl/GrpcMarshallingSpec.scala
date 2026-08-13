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

import java.util.concurrent.{ CompletableFuture, TimeUnit }

import scala.concurrent.Await
import scala.concurrent.duration._

import com.google.protobuf.{ Any => ProtobufAny, ByteString => ProtobufByteString }
import io.grpc.StatusException
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.grpc.internal.{ AbstractGrpcProtocol, GrpcProtocolNative, Identity, MissingParameterException }
import pekko.http.scaladsl.model.HttpEntity
import pekko.stream.SystemMaterializer
import pekko.util.ByteString

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

    "decode a strict native identity frame directly" in {
      val serializer = new GoogleProtobufSerializer(ProtobufAny.parser())
      val message =
        ProtobufAny.newBuilder().setTypeUrl("benchmark").setValue(ProtobufByteString.copyFromUtf8("payload")).build()
      val payload = serializer.serialize(message)
      val frame = AbstractGrpcProtocol.encodeFrameData(payload, isCompressed = false, isTrailer = false)

      GrpcProtocolNative.newReader(Identity).decodeSingleFrame(frame) should be(payload)
    }

    "reject compressed strict native identity frames" in {
      val frame = AbstractGrpcProtocol.encodeFrameData(ByteString(1, 2, 3), isCompressed = true, isTrailer = false)

      a[StatusException] should be thrownBy GrpcProtocolNative.newReader(Identity).decodeSingleFrame(frame)
    }

    "reject truncated strict native identity frames" in {
      a[MissingParameterException] should be thrownBy
      GrpcProtocolNative
        .newReader(Identity)
        .decodeSingleFrame(ByteString(0, 0, 0, 0))
    }

    "reject strict native identity frames with missing payload bytes" in {
      a[MissingParameterException] should be thrownBy
      GrpcProtocolNative
        .newReader(Identity)
        .decodeSingleFrame(ByteString(0, 0, 0, 0, 3, 1, 2))
    }

    "reject strict native identity frames with trailing bytes" in {
      val frame = AbstractGrpcProtocol.encodeFrameData(ByteString(1, 2, 3), isCompressed = false, isTrailer = false)

      an[IllegalStateException] should be thrownBy
      GrpcProtocolNative
        .newReader(Identity)
        .decodeSingleFrame(frame ++ ByteString(4))
    }

    "recover a failed unary response stage" in {
      val system = ActorSystem("GrpcMarshallingSpec")
      try {
        val serializer = new GoogleProtobufSerializer(ProtobufAny.parser())
        val responseFuture = new CompletableFuture[ProtobufAny]()
        responseFuture.completeExceptionally(new RuntimeException("boom"))

        val response =
          GrpcMarshalling
            .handleUnaryResponse(
              responseFuture,
              serializer,
              GrpcProtocolNative.newWriter(Identity),
              system,
              GrpcExceptionHandler.defaultMapper)
            .toCompletableFuture
            .get(10, TimeUnit.SECONDS)

        response.getHeader("grpc-status").get().value() should be("13")
      } finally {
        Await.result(system.terminate(), 10.seconds)
      }
    }
  }
}
