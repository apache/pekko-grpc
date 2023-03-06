/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.scaladsl

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.grpc.internal.{ GrpcProtocolNative, GrpcRequestHelpers, Identity }
import pekko.grpc.scaladsl.headers.`Status`
import pekko.http.scaladsl.model.{ AttributeKeys, HttpEntity, HttpRequest, HttpResponse }
import pekko.http.scaladsl.model.HttpEntity.{ Chunked, LastChunk, Strict }
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.testkit.TestKit
import pekko.util.ByteString
import io.grpc.testing.integration.test.TestService
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.Future

class GrpcExceptionHandlerSpec
    extends TestKit(ActorSystem("GrpcExceptionHandlerSpec"))
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures {
  implicit val ec = system.dispatcher

  "The default ExceptionHandler" should {
    "produce an INVALID_ARGUMENT error when the expected parameter is not found" in {
      implicit val serializer = TestService.Serializers.SimpleRequestSerializer
      implicit val marshaller = GrpcProtocolNative.newWriter(Identity)
      // request that is missing the actual data
      val unmarshallableRequest = HttpRequest(entity = HttpEntity.empty(GrpcProtocolNative.contentType))

      val result: Future[HttpResponse] = GrpcMarshalling
        .unmarshal(unmarshallableRequest)
        .map(_ => HttpResponse())
        .recoverWith(GrpcExceptionHandler.default)

      val response = result.futureValue
      response.entity match {
        case Chunked(_, chunks) =>
          chunks.runWith(Sink.seq).futureValue match {
            case Seq(LastChunk("", List(`Status`("3")))) => // ok
          }
        case _: Strict =>
          response.attribute(AttributeKeys.trailer).get.headers.contains("grpc-status" -> "3")
        case other =>
          fail(s"Unexpected [$other]")
      }
    }

    import example.myapp.helloworld.grpc.helloworld._
    object ExampleImpl extends GreeterService {

      // #streaming
      import org.apache.pekko
      import pekko.NotUsed
      import pekko.stream.scaladsl.Source

      // #streaming

      // #unary
      // #streaming
      import pekko.grpc.GrpcServiceException
      import io.grpc.Status

      val exceptionMetadata = new MetadataBuilder()
        .addText("test-text", "test-text-data")
        .addBinary("test-binary-bin", ByteString("test-binary-data"))
        .build()

      // #unary
      // #streaming

      // #unary
      // ...

      def sayHello(in: HelloRequest): Future[HelloReply] = {
        if (in.name.isEmpty)
          Future.failed(
            new GrpcServiceException(Status.INVALID_ARGUMENT.withDescription("No name found"), exceptionMetadata))
        else
          Future.successful(HelloReply(s"Hi ${in.name}!"))
      }
      // #unary

      lazy val myResponseSource: Source[HelloReply, NotUsed] = ???

      // #streaming
      def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] = {
        if (in.name.isEmpty)
          Source.failed(
            new GrpcServiceException(Status.INVALID_ARGUMENT.withDescription("No name found"), exceptionMetadata))
        else
          myResponseSource
      }
      // #streaming

      def itKeepsTalking(
          in: pekko.stream.scaladsl.Source[example.myapp.helloworld.grpc.helloworld.HelloRequest,
            pekko.NotUsed]): scala.concurrent.Future[example.myapp.helloworld.grpc.helloworld.HelloReply] =
        ???
      def streamHellos(
          in: pekko.stream.scaladsl.Source[example.myapp.helloworld.grpc.helloworld.HelloRequest,
            pekko.NotUsed]): pekko.stream.scaladsl.Source[
        example.myapp.helloworld.grpc.helloworld.HelloReply, pekko.NotUsed] = ???

    }

    "return the correct user-supplied status for a unary call" in {
      implicit val serializer =
        example.myapp.helloworld.grpc.helloworld.GreeterService.Serializers.HelloRequestSerializer
      implicit val writer = GrpcProtocolNative.newWriter(Identity)

      val request = GrpcRequestHelpers(s"/${GreeterService.name}/SayHello", List.empty, Source.single(HelloRequest("")))

      val reply = GreeterServiceHandler(ExampleImpl).apply(request).futureValue

      val lastChunk = reply.entity.asInstanceOf[Chunked].chunks.runWith(Sink.last).futureValue.asInstanceOf[LastChunk]
      // Invalid argument is '3' https://github.com/grpc/grpc/blob/master/doc/statuscodes.md
      val statusHeader = lastChunk.trailer.find { _.name == "grpc-status" }
      statusHeader.map(_.value()) should be(Some("3"))
      val statusMessageHeader = lastChunk.trailer.find { _.name == "grpc-message" }
      statusMessageHeader.map(_.value()) should be(Some("No name found"))

      val metadata = MetadataBuilder.fromHeaders(lastChunk.trailer)
      metadata.getText("test-text") should be(Some("test-text-data"))
      metadata.getBinary("test-binary-bin") should be(Some(ByteString("test-binary-data")))
    }

    "return the correct user-supplied status for a streaming call" in {
      implicit val serializer =
        example.myapp.helloworld.grpc.helloworld.GreeterService.Serializers.HelloRequestSerializer
      implicit val writer = GrpcProtocolNative.newWriter(Identity)

      val request =
        GrpcRequestHelpers(s"/${GreeterService.name}/ItKeepsReplying", List.empty, Source.single(HelloRequest("")))

      val reply = GreeterServiceHandler(ExampleImpl).apply(request).futureValue

      val lastChunk = reply.entity.asInstanceOf[Chunked].chunks.runWith(Sink.last).futureValue.asInstanceOf[LastChunk]
      // Invalid argument is '3' https://github.com/grpc/grpc/blob/master/doc/statuscodes.md
      val statusHeader = lastChunk.trailer.find { _.name == "grpc-status" }
      statusHeader.map(_.value()) should be(Some("3"))
      val statusMessageHeader = lastChunk.trailer.find { _.name == "grpc-message" }
      statusMessageHeader.map(_.value()) should be(Some("No name found"))

      val metadata = MetadataBuilder.fromHeaders(lastChunk.trailer)
      metadata.getText("test-text") should be(Some("test-text-data"))
      metadata.getBinary("test-binary-bin") should be(Some(ByteString("test-binary-data")))
    }
  }
}
