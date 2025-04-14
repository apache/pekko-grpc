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

package org.apache.pekko.grpc.scaladsl

import com.typesafe.config.ConfigFactory
import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.grpc.internal.{GrpcProtocolNative, GrpcRequestHelpers, Identity, TelemetryExtension, TelemetrySpi}
import pekko.grpc.GrpcProtocol
import pekko.http.javadsl.model.{HttpRequest, HttpResponse}
import pekko.stream.scaladsl.Source
import pekko.testkit.TestKit
import example.myapp.helloworld.grpc.helloworld.{GreeterService, GreeterServiceHandler, HelloRequest}
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class TelemetrySpec
    extends TestKit(
      ActorSystem(
        "TelemetrySpec",
        ConfigFactory
          .parseString("""pekko.grpc.telemetry-class = "org.apache.pekko.grpc.scaladsl.CollectingTelemetrySpi" """)
          .withFallback(ConfigFactory.load())))
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures {
  "The client-side telemetry hook" should {
    "pick up matched requests" in {
      val handler = GreeterServiceHandler(new CountingGreeterServiceImpl)
      implicit val ser: ScalapbProtobufSerializer[HelloRequest] = GreeterService.Serializers.HelloRequestSerializer
      implicit val writer: GrpcProtocol.GrpcProtocolWriter = GrpcProtocolNative.newWriter(Identity)
      handler(
        GrpcRequestHelpers(
          s"https://localhost/${GreeterService.name}/SayHello",
          Nil,
          Source.single(HelloRequest("Joe")))).futureValue

      val spi = TelemetryExtension(system).spi.asInstanceOf[CollectingTelemetrySpi]
      spi.requests.size should be(1)
      spi.requests(0) match {
        case (prefix, method, request) =>
          prefix should be(GreeterService.name)
          method should be("SayHello")
          request.entity.getContentType should be(GrpcProtocolNative.contentType)
      }

      spi.responses.size should be(1)
      spi.responses(0) match {
        case (prefix, method, request, response) =>
          prefix should be(GreeterService.name)
          method should be("SayHello")
          request.entity.getContentType should be(GrpcProtocolNative.contentType)

          response.status should be(StatusCodes.OK)
          response.entity.getContentType should be(GrpcProtocolNative.contentType)
      }
    }
  }
}

class CollectingTelemetrySpi extends TelemetrySpi {
  @volatile
  var requests: List[(String, String, HttpRequest)] = Nil

  @volatile
  var responses: List[(String, String, HttpRequest, HttpResponse)] = Nil

  override def onRequest[T <: HttpRequest](prefix: String, method: String, request: T): T = {
    requests :+= (prefix, method, request)
    request
  }

  override def onResponse[Req <: HttpRequest, Rep <: HttpResponse](prefix: String, method: String, request: Req, response: Rep): Rep = {
    responses :+= (prefix, method, request, response)
    response
  }
  //  override def onResponse[Req <: HttpRequest, Rep <: HttpResponse](prefix: String, method: String, request: Req, response: Rep): Rep = {
//  }
}
