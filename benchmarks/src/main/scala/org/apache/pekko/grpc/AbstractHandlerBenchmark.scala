/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.pekko.grpc

import example.myapp.helloworld.grpc.{ GreeterService, HelloRequest }
import org.apache.pekko
import org.openjdk.jmh.annotations.TearDown
import pekko.actor.ActorSystem
import pekko.grpc.internal.{ GrpcProtocolNative, Identity }
import pekko.grpc.scaladsl.GrpcMarshalling
import pekko.http.scaladsl.model.{ HttpEntity, HttpRequest, Uri }
import pekko.stream.scaladsl.Source
import pekko.stream.{ Materializer, SystemMaterializer }

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ Await, ExecutionContext }

abstract class AbstractHandlerBenchmark extends CommonBenchmark {
  val responseRepeats = 5
  val requestRepeats = 5

  implicit val system: ActorSystem = ActorSystem("bench")
  implicit val mat: Materializer = SystemMaterializer(system).materializer
  implicit val ec: ExecutionContext = mat.executionContext
  private implicit val helloRequestSerializer: ProtobufSerializer[HelloRequest] =
    GreeterService.Serializers.HelloRequestSerializer

  implicit val writer: GrpcProtocol.GrpcProtocolWriter = GrpcProtocolNative.newWriter(Identity)
  implicit val reader: GrpcProtocol.GrpcProtocolReader = GrpcProtocolNative.newReader(Identity)
  private val requestMessage: HelloRequest = HelloRequest("Alice")

  private val serviceBaseUri = s"https://unused.example/${GreeterService.name}"

  private def request(method: String): HttpRequest =
    GrpcMarshalling.marshalRequest(Uri(s"${serviceBaseUri}/$method"), requestMessage)

  private def streamRequest(
      method: String): HttpRequest = GrpcMarshalling.marshalStreamRequest(Uri(s"${serviceBaseUri}/$method"),
    Source.repeat(requestMessage).take(requestRepeats))

  private def chunked(request: HttpRequest): HttpRequest =
    request.withEntity(HttpEntity.Chunked(writer.contentType, request.entity.dataBytes.map(HttpEntity.Chunk(_))))

  private def strict(request: HttpRequest): HttpRequest =
    request.withEntity(Await.result(request.entity.toStrict(5.seconds), 5.seconds))

  val strictSayHelloRequest: HttpRequest = strict(request("SayHello"))

  val nonstrictSayHelloRequest: HttpRequest = chunked(strictSayHelloRequest)

  val strictItKeepsReplyingRequest: HttpRequest = strict(request("ItKeepsReplying"))

  val nonstrictItKeepsReplyingRequest: HttpRequest = chunked(strictItKeepsReplyingRequest)

  val nonstrictItKeepsTalkingRequest: HttpRequest = streamRequest("ItKeepsTalking")

  val strictItKeepsTalkingRequest: HttpRequest = strict(nonstrictItKeepsTalkingRequest)

  val nonstrictStreamHellosRequest: HttpRequest = streamRequest("StreamHellos")

  val strictStreamHellosRequest: HttpRequest = strict(nonstrictStreamHellosRequest)

  @TearDown
  def tearDown(): Unit =
    system.terminate()

}
