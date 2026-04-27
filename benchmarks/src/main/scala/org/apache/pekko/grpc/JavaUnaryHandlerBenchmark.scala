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

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

import scala.annotation.nowarn
import scala.collection.immutable
import scala.concurrent.Await
import scala.concurrent.duration.Duration

import com.google.protobuf.{ Any => JavaAny }
import com.google.protobuf.{ ByteString => JavaByteString }
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.infra.Blackhole

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.grpc.internal.AbstractGrpcProtocol
import pekko.grpc.internal.Codecs
import pekko.grpc.internal.GrpcProtocolNative
import pekko.grpc.internal.Identity
import pekko.grpc.javadsl.{ GoogleProtobufSerializer, GrpcExceptionHandler => JGrpcExceptionHandler }
import pekko.grpc.javadsl.{ GrpcMarshalling => JGrpcMarshalling }
import pekko.grpc.scaladsl.headers.`Message-Accept-Encoding`
import pekko.grpc.scaladsl.headers.`Message-Encoding`
import pekko.http.javadsl.model.{ HttpRequest => JHttpRequest }
import pekko.http.javadsl.model.{ HttpResponse => JHttpResponse }
import pekko.http.scaladsl.model.HttpEntity
import pekko.http.scaladsl.model.HttpMethods
import pekko.http.scaladsl.model.HttpRequest
import pekko.http.scaladsl.model.HttpResponse
import pekko.http.scaladsl.model.TransferEncodings
import pekko.http.scaladsl.model.Uri
import pekko.stream.Materializer
import pekko.stream.SystemMaterializer
import pekko.stream.scaladsl.Sink

class JavaUnaryHandlerBenchmark extends CommonBenchmark {
  private implicit val system: ActorSystem = ActorSystem("bench")
  private val mat: Materializer = SystemMaterializer(system).materializer

  private val writer = GrpcProtocolNative.newWriter(Identity)
  private val serializer = new GoogleProtobufSerializer(JavaAny.parser())
  private val requestMessage: JavaAny =
    JavaAny.newBuilder().setTypeUrl("benchmark").setValue(JavaByteString.copyFromUtf8("request")).build()
  private val responseMessage: JavaAny =
    JavaAny.newBuilder().setTypeUrl("benchmark").setValue(JavaByteString.copyFromUtf8("response")).build()
  private val eHandler = JGrpcExceptionHandler.defaultMapper

  private val request: JHttpRequest = {
    val data =
      AbstractGrpcProtocol.encodeFrameData(
        serializer.serialize(requestMessage),
        isCompressed = false,
        isTrailer = false)

    HttpRequest(
      method = HttpMethods.POST,
      uri = Uri("https://unused.example/benchmark/Unary"),
      headers = immutable.Seq(
        `Message-Encoding`(writer.messageEncoding.name),
        `Message-Accept-Encoding`(Codecs.supportedCodecs.map(_.name).mkString(",")),
        pekko.http.scaladsl.model.headers.TE(TransferEncodings.trailers)),
      entity = HttpEntity.Strict(writer.contentType, data))
  }

  private val unsupportedMediaType: CompletionStage[JHttpResponse] =
    CompletableFuture.completedFuture(
      pekko.http.javadsl.model.HttpResponse
        .create()
        .withStatus(pekko.http.javadsl.model.StatusCodes.UNSUPPORTED_MEDIA_TYPE))

  private val generatedStyleHandler: JHttpRequest => CompletionStage[JHttpResponse] =
    request =>
      JGrpcMarshalling
        .negotiated[JHttpResponse](
          request,
          (reader, writer) => {
            val response: CompletionStage[JHttpResponse] =
              request.entity() match {
                case strict: pekko.http.scaladsl.model.HttpEntity.Strict =>
                  try {
                    JGrpcMarshalling.handleUnaryResponse(
                      invoke(serializer.deserialize(reader.decodeSingleFrame(strict.data))),
                      serializer,
                      writer,
                      system,
                      eHandler)
                  } catch {
                    case error: Throwable => JGrpcMarshalling.handleUnaryFailure(error)
                  }
                case _ =>
                  JGrpcMarshalling
                    .unmarshal(request.entity(), serializer, mat, reader)
                    .thenCompose(in => invoke(in))
                    .thenApply(out => JGrpcMarshalling.marshal(out, serializer, writer, system, eHandler))
              }
            response.exceptionally(error => JGrpcExceptionHandler.standard(error, eHandler, writer, system))
          })
        .orElseGet(() => unsupportedMediaType)

  private val oldStyleHandler: JHttpRequest => CompletionStage[JHttpResponse] =
    request =>
      JGrpcMarshalling
        .negotiated[JHttpResponse](
          request,
          (reader, writer) =>
            JGrpcMarshalling
              .unmarshal(request.entity(), serializer, mat, reader)
              .thenCompose(in => invoke(in))
              .thenApply(out => JGrpcMarshalling.marshal(out, serializer, writer, system, eHandler))
              .exceptionally(error => JGrpcExceptionHandler.standard(error, eHandler, writer, system)))
        .orElseGet(() => unsupportedMediaType)

  @Benchmark
  def generatedStyleUnaryStrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(generatedStyleHandler(request).toCompletableFuture.get(), blackhole)

  @Benchmark
  def oldStyleUnaryStrictRequestProcessing(blackhole: Blackhole): Unit =
    consumeResponse(oldStyleHandler(request).toCompletableFuture.get(), blackhole)

  private def invoke(@nowarn("msg=never used") in: JavaAny): CompletionStage[JavaAny] =
    CompletableFuture.completedFuture(responseMessage)

  private def consumeResponse(response: JHttpResponse, blackhole: Blackhole): Unit = {
    val scalaResponse = response.asInstanceOf[HttpResponse]
    blackhole.consume(scalaResponse.status)
    scalaResponse.entity match {
      case HttpEntity.Strict(_, data) =>
        blackhole.consume(data)
      case _ =>
        Await.result(scalaResponse.entity.dataBytes.runWith(Sink.ignore)(mat), Duration.Inf)
    }
  }

  @TearDown
  def tearDown(): Unit =
    system.terminate()
}
