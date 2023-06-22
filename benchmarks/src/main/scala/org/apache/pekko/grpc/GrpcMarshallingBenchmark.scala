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

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.grpc.internal.{ GrpcProtocolNative, Identity }
import pekko.grpc.scaladsl.{ GrpcMarshalling, ScalapbProtobufSerializer }
import pekko.http.scaladsl.model.HttpResponse
import pekko.stream.scaladsl.Source
import grpc.reflection.v1alpha.reflection._
import org.openjdk.jmh.annotations._

// Microbenchmarks for GrpcMarshalling.
// Does not actually benchmarks the actual marshalling because we dont consume the HttpResponse
class GrpcMarshallingBenchmark extends CommonBenchmark {
  implicit val system: ActorSystem = ActorSystem("bench")
  implicit val writer: GrpcProtocol.GrpcProtocolWriter = GrpcProtocolNative.newWriter(Identity)
  implicit val reader: GrpcProtocol.GrpcProtocolReader = GrpcProtocolNative.newReader(Identity)
  implicit val serializer: ScalapbProtobufSerializer[ServerReflectionRequest] =
    ServerReflection.Serializers.ServerReflectionRequestSerializer

  @Benchmark
  def marshall(): HttpResponse = {
    GrpcMarshalling.marshal(ServerReflectionRequest())
  }

  @Benchmark
  def marshallStream(): HttpResponse = {
    GrpcMarshalling.marshalStream(Source.repeat(ServerReflectionRequest()).take(10000))
  }

  @TearDown
  def tearDown(): Unit = {
    system.terminate()
  }
}
