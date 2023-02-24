/*
 * Copyright (C) 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem

import org.apache.pekko.grpc.internal.Identity
import org.apache.pekko.grpc.internal.GrpcRequestHelpers
import org.apache.pekko.grpc.internal.GrpcProtocolNative

import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.Uri

import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source

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
