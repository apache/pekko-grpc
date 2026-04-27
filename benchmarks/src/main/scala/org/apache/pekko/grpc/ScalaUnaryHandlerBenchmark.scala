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

import scala.collection.immutable
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.infra.Blackhole

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.grpc.internal.AbstractGrpcProtocol
import pekko.grpc.internal.Codecs
import pekko.grpc.internal.GrpcProtocolNative
import pekko.grpc.internal.Identity
import pekko.grpc.internal.TelemetryExtension
import pekko.grpc.scaladsl.GrpcExceptionHandler
import pekko.grpc.scaladsl.GrpcMarshalling
import pekko.grpc.scaladsl.headers.`Message-Accept-Encoding`
import pekko.grpc.scaladsl.headers.`Message-Encoding`
import pekko.http.scaladsl.model.HttpEntity
import pekko.http.scaladsl.model.HttpMethods
import pekko.http.scaladsl.model.HttpRequest
import pekko.http.scaladsl.model.HttpResponse
import pekko.http.scaladsl.model.StatusCodes
import pekko.http.scaladsl.model.TransferEncodings
import pekko.http.scaladsl.model.Uri
import pekko.stream.Materializer
import pekko.stream.SystemMaterializer
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source

import example.myapp.helloworld.grpc.GreeterService
import example.myapp.helloworld.grpc.GreeterServiceHandler
import example.myapp.helloworld.grpc.HelloReply
import example.myapp.helloworld.grpc.HelloRequest

class ScalaUnaryHandlerBenchmark extends CommonBenchmark {
  implicit val system: ActorSystem = ActorSystem("bench")
  private implicit val mat: Materializer = SystemMaterializer(system).materializer
  private implicit val ec: ExecutionContext = mat.executionContext

  private val writer = GrpcProtocolNative.newWriter(Identity)
  private val requestMessage = HelloRequest("Alice")
  private val responseMessage = HelloReply("Hello, Alice")
  private val implementation = new BenchmarkGreeterService(responseMessage)

  private val request: HttpRequest = {
    val data =
      AbstractGrpcProtocol.encodeFrameData(
        GreeterService.Serializers.HelloRequestSerializer.serialize(requestMessage),
        isCompressed = false,
        isTrailer = false)

    HttpRequest(
      method = HttpMethods.POST,
      uri = Uri("https://unused.example/" + GreeterService.name + "/SayHello"),
      headers = immutable.Seq(
        `Message-Encoding`(writer.messageEncoding.name),
        `Message-Accept-Encoding`(Codecs.supportedCodecs.map(_.name).mkString(",")),
        pekko.http.scaladsl.model.headers.TE(TransferEncodings.trailers)),
      entity = HttpEntity.Strict(writer.contentType, data))
  }

  private val generatedHandler: HttpRequest => Future[HttpResponse] =
    GreeterServiceHandler(implementation)

  private val oldStyleHandler: HttpRequest => Future[HttpResponse] = {
    val notFound = Future.successful(HttpResponse(StatusCodes.NotFound))
    val unsupportedMediaType = Future.successful(HttpResponse(StatusCodes.UnsupportedMediaType))
    val spi = TelemetryExtension(system).spi
    val eHandler = GrpcExceptionHandler.defaultMapper _

    import GreeterService.Serializers._

    request =>
      request.uri.path match {
        case Uri.Path.Slash(
              Uri.Path.Segment(
                GreeterService.name,
                Uri.Path.Slash(Uri.Path.Segment("SayHello", Uri.Path.Empty)))) =>
          val requestWithTelemetry = spi.onRequest(GreeterService.name, "SayHello", request)
          GrpcMarshalling
            .negotiated(requestWithTelemetry,
              (reader, writer) =>
                GrpcMarshalling
                  .unmarshal(requestWithTelemetry.entity)(HelloRequestSerializer, mat, reader)
                  .flatMap(implementation.sayHello)
                  .map(e => GrpcMarshalling.marshal(e)(HelloReplySerializer, writer, system))
                  .recoverWith(GrpcExceptionHandler.from(eHandler(system))(system, writer)))
            .getOrElse(unsupportedMediaType)
        case _ =>
          notFound
      }
  }

  @Benchmark
  def generatedUnaryStrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(Await.result(generatedHandler(request), Duration.Inf), blackhole)

  @Benchmark
  def oldStyleUnaryStrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(Await.result(oldStyleHandler(request), Duration.Inf), blackhole)

  private def consumeResponse(response: HttpResponse, blackhole: Blackhole): Unit = {
    blackhole.consume(response.status)
    response.entity match {
      case HttpEntity.Strict(_, data) =>
        blackhole.consume(data)
      case _ =>
        Await.result(response.entity.dataBytes.runWith(Sink.ignore), Duration.Inf)
    }
  }

  @TearDown
  def tearDown(): Unit =
    system.terminate()

  private final class BenchmarkGreeterService(response: HelloReply) extends GreeterService {
    override def sayHello(in: HelloRequest): Future[HelloReply] =
      Future.successful(response)

    override def itKeepsTalking(in: Source[HelloRequest, NotUsed]): Future[HelloReply] =
      throw new UnsupportedOperationException("itKeepsTalking")

    override def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] =
      throw new UnsupportedOperationException("itKeepsReplying")

    override def streamHellos(in: Source[HelloRequest, NotUsed]): Source[HelloReply, NotUsed] =
      throw new UnsupportedOperationException("streamHellos")
  }
}
