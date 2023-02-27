/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.scaladsl

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.grpc.internal.{ GrpcProtocolNative, GrpcRequestHelpers, Identity }
import org.apache.pekko.grpc.scaladsl.headers.`Status`
import org.apache.pekko.http.scaladsl.model.{ AttributeKeys, HttpEntity, HttpRequest, HttpResponse }
import org.apache.pekko.http.scaladsl.model.HttpEntity.{ Chunked, LastChunk, Strict }
import org.apache.pekko.stream.scaladsl.{ Sink, Source }
import org.apache.pekko.testkit.TestKit
import org.apache.pekko.util.ByteString
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
      import org.apache.pekko.NotUsed
      import org.apache.pekko.stream.scaladsl.Source

      // #streaming

      // #unary
      // #streaming
      import org.apache.pekko.grpc.GrpcServiceException
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
          in: org.apache.pekko.stream.scaladsl.Source[example.myapp.helloworld.grpc.helloworld.HelloRequest,
            org.apache.pekko.NotUsed]): scala.concurrent.Future[example.myapp.helloworld.grpc.helloworld.HelloReply] =
        ???
      def streamHellos(
          in: org.apache.pekko.stream.scaladsl.Source[example.myapp.helloworld.grpc.helloworld.HelloRequest,
            org.apache.pekko.NotUsed]): org.apache.pekko.stream.scaladsl.Source[
        example.myapp.helloworld.grpc.helloworld.HelloReply, org.apache.pekko.NotUsed] = ???

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
