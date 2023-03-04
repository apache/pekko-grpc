/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem

import pekko.grpc.internal.Identity
import pekko.grpc.internal.GrpcRequestHelpers
import pekko.grpc.internal.GrpcProtocolNative

import pekko.http.scaladsl.model.HttpRequest
import pekko.http.scaladsl.model.HttpResponse
import pekko.http.scaladsl.model.StatusCodes
import pekko.http.scaladsl.model.Uri

import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source

import org.openjdk.jmh.annotations._

import grpc.reflection.v1alpha.reflection._

class HandlerProcessingBenchmark extends CommonBenchmark {
  implicit val system = ActorSystem("bench")
  implicit val writer = GrpcProtocolNative.newWriter(Identity)

  val in = Source.repeat(ServerReflectionRequest()).take(10000)
  val request: HttpRequest = {
    implicit val serializer = ServerReflection.Serializers.ServerReflectionRequestSerializer
    GrpcRequestHelpers(Uri("https://unused.example/" + ServerReflection.name + "/ServerReflectionInfo"), Nil, in)
  }

  val handler: HttpRequest => Future[HttpResponse] = ServerReflectionHandler(new ServerReflection {
    override def serverReflectionInfo(
        in: Source[ServerReflectionRequest, NotUsed]): Source[ServerReflectionResponse, NotUsed] =
      in.map(_ => ServerReflectionResponse())
  })

  @Benchmark
  @OperationsPerInvocation(10000)
  def streamingRequestProcessing(): Unit = {
    val response = Await.result(handler(request), Duration.Inf)
    // Blackhole the response
    Await.result(response.entity.dataBytes.runWith(Sink.ignore), Duration.Inf)
    assert(response.status == StatusCodes.OK)
  }

  @TearDown
  def tearDown(): Unit = {
    system.terminate()
  }
}
