/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.internal

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.grpc.GrpcResponseMetadata
import pekko.grpc.scaladsl.headers.PercentEncoding
import pekko.http.scaladsl.model.HttpEntity.Strict
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.StatusCodes._
import pekko.http.scaladsl.model.headers.RawHeader
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.testkit.TestKit
import pekko.util.ByteString
import io.grpc.{ CallOptions, Deadline, Metadata, Status, StatusRuntimeException }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpecLike

class PekkoHttpClientUtilsSpec extends TestKit(ActorSystem()) with AnyWordSpecLike with Matchers with ScalaFutures {
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val patience: PatienceConfig =
    PatienceConfig(5.seconds, Span(100, org.scalatest.time.Millis))

  "The conversion from HttpResponse to Source" should {
    "map a strict 404 response to a failed stream" in {
      val response =
        Future.successful(HttpResponse(NotFound, entity = Strict(GrpcProtocolNative.contentType, ByteString.empty)))
      val source = PekkoHttpClientUtils.responseToSource(response, null)

      val failure = source.run().failed.futureValue
      failure shouldBe a[StatusRuntimeException]
      // https://github.com/grpc/grpc/blob/master/doc/http-grpc-status-mapping.md
      failure.asInstanceOf[StatusRuntimeException].getStatus.getCode should be(Status.Code.UNIMPLEMENTED)
    }

    "map a strict 200 response with non-0 gRPC error code to a failed stream" in {
      val responseHeaders = RawHeader("grpc-status", "9") ::
        RawHeader("custom-key", "custom-value-in-header") ::
        RawHeader("custom-key-bin", ByteString("custom-trailer-value").encodeBase64.utf8String) ::
        Nil
      val response =
        Future.successful(HttpResponse(OK, responseHeaders, Strict(GrpcProtocolNative.contentType, ByteString.empty)))
      val source = PekkoHttpClientUtils.responseToSource(response, null)

      val failure = source.run().failed.futureValue
      failure shouldBe a[StatusRuntimeException]
      failure.asInstanceOf[StatusRuntimeException].getStatus.getCode should be(Status.Code.FAILED_PRECONDITION)
      failure.asInstanceOf[StatusRuntimeException].getTrailers.get(key) should be("custom-value-in-header")
    }

    "percent-decode the grpc-message of a failed response" in {
      // grpc-message travels UTF-8 percent-encoded on the wire; the server side encodes it with
      // `PercentEncoding.Encoder`, so an undecoded client hands the caller the raw escapes
      val message = "quota exceeded: 100% of 5 µs — café"
      val encoded = PercentEncoding.Encoder.encode(message)
      encoded should not be message

      val responseHeaders = RawHeader("grpc-status", "9") :: RawHeader("grpc-message", encoded) :: Nil
      val response =
        Future.successful(HttpResponse(OK, responseHeaders, Strict(GrpcProtocolNative.contentType, ByteString.empty)))

      val failure = PekkoHttpClientUtils.responseToSource(response, null).run().failed.futureValue

      failure.asInstanceOf[StatusRuntimeException].getStatus.getDescription should be(message)
    }

    "leave an unencoded grpc-message alone" in {
      val message = "plain ascii failure"
      val responseHeaders = RawHeader("grpc-status", "9") :: RawHeader("grpc-message", message) :: Nil
      val response =
        Future.successful(HttpResponse(OK, responseHeaders, Strict(GrpcProtocolNative.contentType, ByteString.empty)))

      val failure = PekkoHttpClientUtils.responseToSource(response, null).run().failed.futureValue

      failure.asInstanceOf[StatusRuntimeException].getStatus.getDescription should be(message)
    }

    "not throw away a grpc-message with a broken escape" in {
      // the spec requires implementations not to error on invalid values
      val responseHeaders = RawHeader("grpc-status", "9") :: RawHeader("grpc-message", "broken %ZZ escape") :: Nil
      val response =
        Future.successful(HttpResponse(OK, responseHeaders, Strict(GrpcProtocolNative.contentType, ByteString.empty)))

      val failure = PekkoHttpClientUtils.responseToSource(response, null).run().failed.futureValue

      failure.asInstanceOf[StatusRuntimeException].getStatus.getDescription should be("broken %ZZ escape")
    }

    "map a strict 200 response with non-0 gRPC error code with a trailer to a failed stream with trailer metadata" in {
      val responseHeaders = List(RawHeader("grpc-status", "9"))
      val responseTrailers = Trailer(
        RawHeader("custom-key", "custom-trailer-value") ::
        RawHeader("custom-key-bin", ByteString("custom-trailer-value").encodeBase64.utf8String) ::
        Nil)
      val response = Future.successful(
        new HttpResponse(
          OK,
          responseHeaders,
          Map.empty[AttributeKey[?], Any].updated(AttributeKeys.trailer, responseTrailers),
          Strict(GrpcProtocolNative.contentType, ByteString.empty),
          HttpProtocols.`HTTP/1.1`))
      val source = PekkoHttpClientUtils.responseToSource(response, null)

      val failure = source.run().failed.futureValue
      failure.asInstanceOf[StatusRuntimeException].getStatus.getCode should be(Status.Code.FAILED_PRECONDITION)
      failure.asInstanceOf[StatusRuntimeException].getTrailers.get(key) should be("custom-trailer-value")
      failure.asInstanceOf[StatusRuntimeException].getTrailers.get(keyBin) should be(ByteString("custom-trailer-value"))
    }

    lazy val key = Metadata.Key.of("custom-key", Metadata.ASCII_STRING_MARSHALLER)
    lazy val keyBin = Metadata.Key.of("custom-key-bin", Metadata.BINARY_BYTE_MARSHALLER)
  }

  "applyDeadline" should {
    type Resp = Future[GrpcResponseMetadata]

    "pass source through unchanged when no deadline is set" in {
      val source = Source.single(ByteString(1, 2, 3))
      val options = CallOptions.DEFAULT
      val result = PekkoHttpClientUtils.applyDeadline(source.asInstanceOf[Source[Any, Resp]], options)
      val value = result.runWith(Sink.head).futureValue
      value shouldBe ByteString(1, 2, 3)
    }

    "fail immediately when deadline is already expired" in {
      val source = Source.single(ByteString(1, 2, 3))
      val options = CallOptions.DEFAULT.withDeadline(Deadline.after(0, java.util.concurrent.TimeUnit.MILLISECONDS))
      val result = PekkoHttpClientUtils.applyDeadline(source.asInstanceOf[Source[Any, Resp]], options)
      val failure = result.runWith(Sink.head).failed.futureValue
      failure shouldBe a[StatusRuntimeException]
      failure.asInstanceOf[StatusRuntimeException].getStatus.getCode shouldBe Status.Code.DEADLINE_EXCEEDED
    }

    "fail with DEADLINE_EXCEEDED when source does not complete in time" in {
      // Source that never completes
      val source = Source.maybe[ByteString]
      val options = CallOptions.DEFAULT.withDeadline(Deadline.after(100, java.util.concurrent.TimeUnit.MILLISECONDS))
      val result = PekkoHttpClientUtils.applyDeadline(source.asInstanceOf[Source[Any, Resp]], options)
      val failure = result.runWith(Sink.head).failed.futureValue
      failure shouldBe a[StatusRuntimeException]
      failure.asInstanceOf[StatusRuntimeException].getStatus.getCode shouldBe Status.Code.DEADLINE_EXCEEDED
    }
  }
}
