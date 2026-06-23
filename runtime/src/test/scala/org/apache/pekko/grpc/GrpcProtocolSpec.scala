/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.grpc

import scala.collection.immutable

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.TryValues

import org.apache.pekko
import pekko.grpc.internal.{ Gzip, Identity }
import pekko.grpc.scaladsl.headers
import pekko.http.scaladsl.model.{ ContentTypes, HttpEntity, HttpRequest }

class GrpcProtocolSpec extends AnyWordSpec with Matchers with TryValues {

  private def grpcRequest(encoding: Option[String] = None, acceptEncoding: Option[String] = None): HttpRequest = {
    val hdrs = immutable.Seq.newBuilder[pekko.http.scaladsl.model.HttpHeader]
    encoding.foreach(e => hdrs += headers.`Message-Encoding`(e))
    acceptEncoding.foreach(a => hdrs += headers.`Message-Accept-Encoding`(a))
    HttpRequest(
      entity = HttpEntity.Strict(GrpcProtocolNative.contentType, pekko.util.ByteString.empty),
      headers = hdrs.result())
  }

  "GrpcProtocol.negotiate" should {

    "return Identity reader and Identity writer for native gRPC with no encoding headers" in {
      val result = GrpcProtocol.negotiate(grpcRequest())
      result shouldBe defined
      val (readerTry, writer) = result.get
      readerTry.success.value.codec should be(Identity)
      writer.codec should be(Identity)
    }

    "return Gzip reader when grpc-encoding is gzip" in {
      val result = GrpcProtocol.negotiate(grpcRequest(encoding = Some("gzip")))
      result shouldBe defined
      val (readerTry, _) = result.get
      readerTry.success.value.codec should be(Gzip)
    }

    "return Identity reader when grpc-encoding is identity" in {
      val result = GrpcProtocol.negotiate(grpcRequest(encoding = Some("identity")))
      result shouldBe defined
      val (readerTry, _) = result.get
      readerTry.success.value.codec should be(Identity)
    }

    "return Gzip writer when grpc-accept-encoding contains gzip" in {
      val result = GrpcProtocol.negotiate(grpcRequest(acceptEncoding = Some("gzip")))
      result shouldBe defined
      val (_, writer) = result.get
      writer.codec should be(Gzip)
    }

    "return Identity writer when grpc-accept-encoding is identity only" in {
      val result = GrpcProtocol.negotiate(grpcRequest(acceptEncoding = Some("identity")))
      result shouldBe defined
      val (_, writer) = result.get
      writer.codec should be(Identity)
    }

    "return Gzip writer when grpc-accept-encoding lists gzip first" in {
      val result = GrpcProtocol.negotiate(grpcRequest(acceptEncoding = Some("gzip,identity")))
      result shouldBe defined
      val (_, writer) = result.get
      writer.codec should be(Gzip)
    }

    "return Identity writer when grpc-accept-encoding lists identity before gzip" in {
      val result = GrpcProtocol.negotiate(grpcRequest(acceptEncoding = Some("identity,gzip")))
      result shouldBe defined
      val (_, writer) = result.get
      writer.codec should be(Identity)
    }

    "return pre-computed instance for Identity+Identity" in {
      val result1 = GrpcProtocol.negotiate(grpcRequest())
      val result2 = GrpcProtocol.negotiate(grpcRequest())
      result1 shouldBe defined
      result2 shouldBe defined
      (result1.get eq result2.get) should be(true)
    }

    "return None for unsupported content type" in {
      val request = HttpRequest(
        entity = HttpEntity.Strict(ContentTypes.`text/plain(UTF-8)`, pekko.util.ByteString.empty))
      GrpcProtocol.negotiate(request) should be(None)
    }

    "return None for unknown grpc-encoding" in {
      val result = GrpcProtocol.negotiate(grpcRequest(encoding = Some("zstd")))
      result should be(None)
    }
  }
}
