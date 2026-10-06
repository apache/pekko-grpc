/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.grpc

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.grpc.internal.{ GrpcProtocolNative, GrpcProtocolWeb, Identity }
import pekko.grpc.scaladsl.{ GrpcMarshalling, ScalapbProtobufSerializer }
import pekko.http.scaladsl.model.HttpEntity
import pekko.stream.{ Materializer, SystemMaterializer }
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.util.ByteString
import com.google.protobuf.{ ByteString => ProtobufByteString }
import com.google.protobuf.any.{ Any => ScalapbAny }
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

// Encoding of response data frames, for the native and gRPC-Web protocols without compression.
class FrameWriteBenchmark extends CommonBenchmark {
  @Param(Array("native", "web"))
  var protocol: String = _

  @Param(Array("16", "1024", "65536"))
  var size: Int = _

  implicit val system: ActorSystem = ActorSystem("bench")
  implicit val mat: Materializer = SystemMaterializer(system).materializer
  implicit val serializer: ScalapbProtobufSerializer[ScalapbAny] = new ScalapbProtobufSerializer(ScalapbAny)

  implicit var writer: GrpcProtocol.GrpcProtocolWriter = _
  var message: ScalapbAny = _

  @Setup
  def setup(): Unit = {
    writer = protocol match {
      case "native" => GrpcProtocolNative.newWriter(Identity)
      case "web"    => GrpcProtocolWeb.newWriter(Identity)
    }
    message = ScalapbAny("benchmark", ProtobufByteString.copyFrom(new Array[Byte](size)))
  }

  @TearDown
  def tearDown(): Unit = system.terminate()

  @Benchmark
  def unary(blackhole: Blackhole): Unit =
    consume(GrpcMarshalling.marshal(message).entity, blackhole)

  @Benchmark
  @OperationsPerInvocation(100)
  def stream(blackhole: Blackhole): Unit =
    consume(GrpcMarshalling.marshalStream(Source.repeat(message).take(100)).entity, blackhole)

  private def consume(entity: HttpEntity, blackhole: Blackhole): Unit =
    entity match {
      case HttpEntity.Strict(_, data) => blackhole.consume(data)
      case _ =>
        blackhole.consume(
          Await.result(entity.dataBytes.runWith(Sink.fold(0)((n, b: ByteString) => n + b.length)), Duration.Inf))
    }
}
