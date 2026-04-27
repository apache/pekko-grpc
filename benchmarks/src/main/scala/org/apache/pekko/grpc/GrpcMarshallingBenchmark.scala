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

package org.apache.pekko.grpc

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import com.google.protobuf.{ Any => JavaAny, ByteString => JavaByteString }
import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.grpc.internal.{ AbstractGrpcProtocol, GrpcProtocolNative, Identity }
import pekko.grpc.scaladsl.{ GrpcMarshalling, ScalapbProtobufSerializer }
import pekko.http.scaladsl.model.{ HttpEntity, HttpResponse }
import pekko.stream.SystemMaterializer
import pekko.stream.scaladsl.Source
import io.grpc.reflection.v1.reflection._
import org.openjdk.jmh.annotations._

// Microbenchmarks for GrpcMarshalling.
// Does not actually benchmark response marshalling because we don't consume the HttpResponse.
class GrpcMarshallingBenchmark extends CommonBenchmark {
  implicit val system: ActorSystem = ActorSystem("bench")
  implicit val writer: GrpcProtocol.GrpcProtocolWriter = GrpcProtocolNative.newWriter(Identity)
  implicit val reader: GrpcProtocol.GrpcProtocolReader = GrpcProtocolNative.newReader(Identity)
  implicit val serializer: ScalapbProtobufSerializer[ServerReflectionRequest] =
    ServerReflection.Serializers.ServerReflectionRequestSerializer

  val request = ServerReflectionRequest()
  val entity: HttpEntity.Strict =
    HttpEntity.Strict(
      GrpcProtocolNative.contentType,
      AbstractGrpcProtocol.encodeFrameData(serializer.serialize(request), isCompressed = false, isTrailer = false))

  val javaSerializer = new pekko.grpc.javadsl.GoogleProtobufSerializer(JavaAny.parser())
  val javaRequest: JavaAny =
    JavaAny.newBuilder().setTypeUrl("benchmark").setValue(JavaByteString.copyFromUtf8("payload")).build()
  val javaEntity: pekko.http.javadsl.model.HttpEntity =
    HttpEntity.Strict(
      GrpcProtocolNative.contentType,
      AbstractGrpcProtocol.encodeFrameData(javaSerializer.serialize(javaRequest), isCompressed = false,
        isTrailer = false))

  val mat = SystemMaterializer(system).materializer

  @Benchmark
  def marshall(): HttpResponse = {
    GrpcMarshalling.marshal(request)
  }

  @Benchmark
  def marshallStream(): HttpResponse = {
    GrpcMarshalling.marshalStream(Source.repeat(request).take(10000))
  }

  @Benchmark
  def unmarshallStrict(): ServerReflectionRequest = {
    Await.result(GrpcMarshalling.unmarshal(entity), Duration.Inf)
  }

  @Benchmark
  def unmarshallJavaStrict(): JavaAny = {
    pekko.grpc.javadsl.GrpcMarshalling.unmarshal(javaEntity, javaSerializer, mat, reader).toCompletableFuture.get()
  }

  @Benchmark
  def unmarshallJavaStrictStreamed(): JavaAny = {
    pekko.grpc.javadsl.GrpcMarshalling
      .unmarshal(javaEntity.getDataBytes, javaSerializer, mat, reader)
      .toCompletableFuture
      .get()
  }

  @TearDown
  def tearDown(): Unit = {
    system.terminate()
  }
}
