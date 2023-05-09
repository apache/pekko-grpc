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

package org.apache.pekko.grpc.internal

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.http.scaladsl.model.HttpEntity.Strict
import pekko.http.scaladsl.model.HttpResponse
import pekko.http.scaladsl.model.StatusCodes._
import pekko.http.scaladsl.model.headers.RawHeader
import pekko.testkit.TestKit
import pekko.util.ByteString
import io.grpc.{ Status, StatusRuntimeException }
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
      // https://github.com/grpc/grpc/blob/master/doc/http-grpc-status-mapping.md
      failure.asInstanceOf[StatusRuntimeException].getStatus.getCode should be(Status.Code.UNIMPLEMENTED)
    }

    "map a strict 200 response with non-0 gRPC error code to a failed stream" in {
      val response = Future.successful(
        HttpResponse(OK, List(RawHeader("grpc-status", "9")), Strict(GrpcProtocolNative.contentType, ByteString.empty)))
      val source = PekkoHttpClientUtils.responseToSource(response, null)

      val failure = source.run().failed.futureValue
      failure.asInstanceOf[StatusRuntimeException].getStatus.getCode should be(Status.Code.FAILED_PRECONDITION)
    }
  }
}
