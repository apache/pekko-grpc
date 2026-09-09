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

import example.myapp.helloworld.grpc.GreeterService.Serializers.{ HelloReplySerializer, HelloRequestSerializer }
import example.myapp.helloworld.grpc.{ GreeterService, GreeterServiceHandler, HelloReply, HelloRequest }
import org.apache.pekko
import pekko.NotUsed
import pekko.grpc.internal.TelemetryExtension
import pekko.grpc.scaladsl.{ GrpcExceptionHandler, GrpcMarshalling }
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.util.FastFuture
import pekko.http.scaladsl.util.FastFuture.EnhancedFuture
import pekko.stream.scaladsl.{ Sink, Source }
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.infra.Blackhole

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }

class ScalaHandlerBenchmark extends AbstractHandlerBenchmark {
  private val responseMessage: HelloReply = HelloReply("Hello, Alice")

  private val eHandler = GrpcExceptionHandler.defaultMapper _
  // A typical service implementation, returning an ordinary completed Future.
  private val implementation = new BenchmarkGreeterService(responseMessage)
  // A service implementation that returns an already completed FastFuture, so that every transform
  // the handler chains onto it can also run directly instead of being scheduled.
  private val fastImplementation = new BenchmarkGreeterService(responseMessage) {
    override protected def completedFuture[A](a: A): Future[A] = FastFuture.successful(a)
  }

  private val generatedHandler: HttpRequest => Future[HttpResponse] =
    GreeterServiceHandler(implementation)

  // Minimal handler bypassing handler composition, negotiation and path matching overheads
  private val syntheticSayHelloHandler: HttpRequest => Future[HttpResponse] = (request: HttpRequest) => {
    GrpcMarshalling.handleUnary(request.entity, implementation.sayHello, eHandler)(
      HelloRequestSerializer, HelloReplySerializer, mat, reader, writer, system, ec)
  }

  // Minimal handler bypassing handler composition, negotiation and path matching overheads
  private val syntheticItKeepsTalkingHandler: HttpRequest => Future[HttpResponse] = (request: HttpRequest) => {
    GrpcMarshalling.unmarshalStream(request.entity)(HelloRequestSerializer, mat, reader)
      .flatMap(implementation.itKeepsTalking)
      .map(e => GrpcMarshalling.marshal(e, eHandler)(HelloReplySerializer, writer, system))(ExecutionContext.parasitic)
      .recoverWith(GrpcExceptionHandler.from(eHandler(system.classicSystem))(system, writer))(
        ExecutionContext.parasitic)
  }

  // Pre 2.0 generated code handler
  private def oldStyleHandlerFor(implementation: GreeterService): HttpRequest => Future[HttpResponse] = {
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

  private val oldStyleHandler: HttpRequest => Future[HttpResponse] = oldStyleHandlerFor(implementation)
  private val fastOldStyleHandler: HttpRequest => Future[HttpResponse] = oldStyleHandlerFor(fastImplementation)

  @Benchmark
  def generatedUnaryStrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(Await.result(generatedHandler(strictSayHelloRequest), Duration.Inf), blackhole)

  @Benchmark
  def generatedUnaryNonstrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(Await.result(generatedHandler(nonstrictSayHelloRequest), Duration.Inf), blackhole)

  @Benchmark
  def syntheticUnaryStrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(Await.result(syntheticSayHelloHandler(strictSayHelloRequest), Duration.Inf), blackhole)

  @Benchmark
  def syntheticUnaryNonstrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(Await.result(syntheticSayHelloHandler(nonstrictSayHelloRequest), Duration.Inf), blackhole)

  @Benchmark
  def oldStyleUnaryStrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(Await.result(oldStyleHandler(strictSayHelloRequest), Duration.Inf), blackhole)

  @Benchmark
  def oldStyleUnaryStrictRequestProcessingFastImplementation(blackhole: Blackhole): Unit =
    consumeResponse(Await.result(fastOldStyleHandler(strictSayHelloRequest), Duration.Inf), blackhole)

  @Benchmark
  def oldStyleUnaryNonstrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(Await.result(oldStyleHandler(nonstrictSayHelloRequest), Duration.Inf), blackhole)

  @Benchmark
  def generatedServerStreamingStrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(Await.result(generatedHandler(strictItKeepsReplyingRequest), Duration.Inf), blackhole)

  @Benchmark
  def generatedServerStreamingNonstrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(Await.result(generatedHandler(nonstrictItKeepsReplyingRequest), Duration.Inf), blackhole)

  @Benchmark
  def generatedClientStreamingStrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(Await.result(generatedHandler(strictItKeepsTalkingRequest), Duration.Inf), blackhole)

  @Benchmark
  def generatedClientStreamingNonstrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(Await.result(generatedHandler(nonstrictItKeepsTalkingRequest), Duration.Inf), blackhole)

  @Benchmark
  def syntheticClientStreamingStrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(Await.result(syntheticItKeepsTalkingHandler(strictItKeepsTalkingRequest), Duration.Inf), blackhole)

  @Benchmark
  def syntheticClientStreamingNonstrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(Await.result(syntheticItKeepsTalkingHandler(nonstrictItKeepsTalkingRequest), Duration.Inf),
      blackhole)

  @Benchmark
  def generatedBidiStreamingStrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(Await.result(generatedHandler(strictStreamHellosRequest), Duration.Inf), blackhole)

  @Benchmark
  def generatedBidiStreamingNonstrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(Await.result(generatedHandler(nonstrictStreamHellosRequest), Duration.Inf), blackhole)

  private def consumeResponse(response: HttpResponse, blackhole: Blackhole): Unit = {
    blackhole.consume(response.status)
    response.entity match {
      case HttpEntity.Strict(_, data) =>
        blackhole.consume(data)
      case _ =>
        Await.result(response.entity.dataBytes.runWith(Sink.ignore), Duration.Inf)
    }
  }

  private class BenchmarkGreeterService(responseMessage: HelloReply) extends GreeterService {
    protected def completedFuture[A](a: A): Future[A] = Future.successful(a)

    override def sayHello(in: HelloRequest): Future[HelloReply] = completedFuture(responseMessage)

    override def itKeepsTalking(in: Source[HelloRequest, NotUsed]): Future[HelloReply] =
      in.runWith(Sink.ignore).fast.flatMap(_ => completedFuture(responseMessage))

    override def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] =
      Source.repeat(responseMessage).take(responseRepeats)

    override def streamHellos(in: Source[HelloRequest, NotUsed]): Source[HelloReply, NotUsed] =
      in.map(_ => responseMessage)
  }
}
