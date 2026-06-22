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

import example.myapp.helloworld.jgrpc.GreeterService.Serializers.{ HelloReplySerializer, HelloRequestSerializer }
import example.myapp.helloworld.jgrpc.{ GreeterService, GreeterServiceHandlerFactory, HelloReply, HelloRequest }
import org.apache.pekko
import pekko.NotUsed
import pekko.grpc.javadsl.{ GrpcExceptionHandler, GrpcMarshalling }
import pekko.http.javadsl.model._
import pekko.japi.function
import pekko.stream.javadsl.{ Sink, Source }
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.infra.Blackhole

import java.util.concurrent.{ CompletableFuture, CompletionStage }

class JavaHandlerBenchmark extends AbstractHandlerBenchmark {
  private val responseMessage = HelloReply.newBuilder.setMessage("Hello, Alice").build()
  private val implementation = new BenchmarkGreeterService(responseMessage)

  private val generatedHandler: function.Function[HttpRequest, CompletionStage[HttpResponse]] =
    GreeterServiceHandlerFactory.create(implementation, system.classicSystem)

  // Pre 2.0 generated code handler
  private val oldStyleSayHelloHandler: HttpRequest => CompletionStage[HttpResponse] = {
    val eHandler = GrpcExceptionHandler.defaultMapper
    val unsupportedMediaType =
      CompletableFuture.completedFuture(HttpResponse.create().withStatus(StatusCodes.UNSUPPORTED_MEDIA_TYPE))

    request =>
      GrpcMarshalling
        .negotiated[HttpResponse](
          request,
          (reader, writer) =>
            GrpcMarshalling
              .unmarshal(request.entity(), HelloRequestSerializer, mat, reader)
              .thenCompose(in => implementation.sayHello(in))
              .thenApply(out => GrpcMarshalling.marshal(out, HelloReplySerializer, writer, system, eHandler))
              .exceptionally(error => GrpcExceptionHandler.standard(error, eHandler, writer, system)))
        .orElseGet(() => unsupportedMediaType)
  }

  @Benchmark
  def generatedUnaryStrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(generatedHandler(strictSayHelloRequest).toCompletableFuture.get(), blackhole)

  @Benchmark
  def generatedUnaryNonstrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(generatedHandler(nonstrictSayHelloRequest).toCompletableFuture.get(), blackhole)

  @Benchmark
  def oldStyleUnaryStrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(oldStyleSayHelloHandler(strictSayHelloRequest).toCompletableFuture.get(), blackhole)

  @Benchmark
  def oldStyleUnaryNonstrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(oldStyleSayHelloHandler(nonstrictSayHelloRequest).toCompletableFuture.get(), blackhole)

  @Benchmark
  def generatedServerStreamingStrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(generatedHandler(strictItKeepsReplyingRequest).toCompletableFuture.get(), blackhole)

  @Benchmark
  def generatedServerStreamingNonstrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(generatedHandler(nonstrictItKeepsReplyingRequest).toCompletableFuture.get(), blackhole)

  @Benchmark
  def generatedClientStreamingStrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(generatedHandler(strictItKeepsTalkingRequest).toCompletableFuture.get(), blackhole)

  @Benchmark
  def generatedClientStreamingNonstrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(generatedHandler(nonstrictItKeepsTalkingRequest).toCompletableFuture.get(), blackhole)

  @Benchmark
  def generatedBidiStreamingStrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(generatedHandler(strictStreamHellosRequest).toCompletableFuture.get(), blackhole)

  @Benchmark
  def generatedBidiStreamingNonstrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(generatedHandler(nonstrictStreamHellosRequest).toCompletableFuture.get(), blackhole)

  private def consumeResponse(response: HttpResponse, blackhole: Blackhole): Unit = {
    blackhole.consume(response.status())
    if (response.entity().isStrict) {
      blackhole.consume(response.entity().asInstanceOf[HttpEntity.Strict].getData)
    } else {
      response.entity().getDataBytes.runWith(Sink.ignore(), mat).toCompletableFuture.join()
    }
  }

  private final class BenchmarkGreeterService(response: HelloReply) extends GreeterService {
    override def sayHello(in: HelloRequest): CompletionStage[HelloReply] =
      CompletableFuture.completedFuture(response)

    override def itKeepsTalking(in: Source[HelloRequest, NotUsed]): CompletionStage[HelloReply] =
      in.runWith(Sink.ignore(), system).thenApply(_ => response)

    override def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] =
      Source.repeat(response).take(responseRepeats)

    override def streamHellos(in: Source[HelloRequest, NotUsed]): Source[HelloReply, NotUsed] =
      in.map(_ => response)

  }
}
