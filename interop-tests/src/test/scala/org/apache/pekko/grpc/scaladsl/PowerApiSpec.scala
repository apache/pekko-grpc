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

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.grpc.{ GrpcClientSettings, GrpcProtocol }
import pekko.grpc.GrpcProtocol.TrailerFrame
import pekko.grpc.GrpcResponseMetadata
import pekko.grpc.internal.GrpcEntityHelpers
import pekko.grpc.internal.GrpcProtocolNative
import pekko.grpc.internal.GrpcResponseHelpers
import pekko.grpc.internal.HeaderMetadataImpl
import pekko.grpc.internal.Identity
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.headers.RawHeader
import pekko.http.scaladsl.server.Directives
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source
import pekko.testkit.TestKit
import com.typesafe.config.ConfigFactory
import example.myapp.helloworld.grpc.helloworld.GreeterServiceClient
import example.myapp.helloworld.grpc.helloworld.GreeterServicePowerApiHandler
import example.myapp.helloworld.grpc.helloworld.HelloReply
import example.myapp.helloworld.grpc.helloworld.HelloRequest
import example.myapp.helloworld.grpc.helloworld._
import io.grpc.Status
import org.scalatest.BeforeAndAfter
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpecLike

class PowerApiSpecNetty extends PowerApiSpec("netty")
class PowerApiSpecPekkoHttp extends PowerApiSpec("pekko-http")

abstract class PowerApiSpec(backend: String)
    extends TestKit(ActorSystem(
      "GrpcExceptionHandlerSpec",
      ConfigFactory.parseString(s"""pekko.grpc.client."*".backend = "$backend" """).withFallback(ConfigFactory.load())))
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with Directives
    with BeforeAndAfter
    with BeforeAndAfterAll {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, Span(10, org.scalatest.time.Millis))

  val server: Http.ServerBinding =
    Http().newServerAt("localhost", 0).bind(GreeterServicePowerApiHandler(new PowerGreeterServiceImpl())).futureValue

  var client: GreeterServiceClient = _

  after {
    if (client != null && !client.closed.isCompleted) {
      client.close().futureValue
    }
  }
  override protected def afterAll(): Unit = {
    server.terminate(3.seconds)
    super.afterAll()
  }

  "The power API" should {
    "successfully pass metadata from client to server" in {
      client = GreeterServiceClient(
        GrpcClientSettings.connectToServiceAt("localhost", server.localAddress.getPort).withTls(false))

      client
        .sayHello()
        // No authentication
        .invoke(HelloRequest("Alice"))
        .futureValue
        .message should be("Hello, Alice (not authenticated)")

      client.sayHello().addHeader("Authorization", "foo").invoke(HelloRequest("Alice")).futureValue.message should be(
        "Hello, Alice (authenticated)")
    }

    "successfully pass metadata from server to client" in {
      implicit val serializer: ScalapbProtobufSerializer[HelloReply] = GreeterService.Serializers.HelloReplySerializer
      val specialServer =
        Http()
          .newServerAt("localhost", 0)
          .bind(path(GreeterService.name / "SayHello") {
            implicit val writer: GrpcProtocol.GrpcProtocolWriter = GrpcProtocolNative.newWriter(Identity)
            val trailingMetadata = new HeaderMetadataImpl(List(RawHeader("foo", "bar")))
            complete(
              GrpcResponseHelpers(
                Source.single(HelloReply("Hello there!")),
                trail = Source.single(GrpcEntityHelpers.trailer(Status.OK, trailingMetadata)))
                .addHeader(RawHeader("baz", "qux")))
          })
          .futureValue

      client = GreeterServiceClient(
        GrpcClientSettings.connectToServiceAt("localhost", specialServer.localAddress.getPort).withTls(false))

      val response = client
        .sayHello()
        // No authentication
        .invokeWithMetadata(HelloRequest("Alice"))
        .futureValue

      response.value.message should be("Hello there!")
      response.headers.getText("baz").get should be("qux")
      response.trailers.futureValue.getText("foo").get should be("bar")
    }

    "(on streamed calls) redeem the headers future as soon as they're available (and trailers future when trailers arrive)" in {

      // invoking streamed calls using the power API materializes a Future[GrpcResponseMetadata]
      // that should redeem as soon as the HEADERS is consumed. Then, the GrpcResponseMetadata instance
      // contains another Future that will redeem when receiving the trailers.
      client = GreeterServiceClient(
        GrpcClientSettings.connectToServiceAt("localhost", server.localAddress.getPort).withTls(false))

      val p = Promise[HelloRequest]()
      val requests: Source[HelloRequest, NotUsed] = Source.single(HelloRequest("Alice")).concat(Source.future(p.future))

      val responseSource: Source[HelloReply, Future[GrpcResponseMetadata]] =
        client.streamHellos().invokeWithMetadata(requests)

      val headers: Future[GrpcResponseMetadata] = responseSource.to(Sink.ignore).run()

      // blocks progress until redeeming `headers`
      val trailers = headers.futureValue.trailers

      // Don't send the finalization message until the headers future was redeemed (see above)
      trailers.isCompleted should be(false)
      p.trySuccess(HelloRequest("ByeBye"))

      trailers.futureValue // the trailers future eventually completes

    }

    "successfully pass metadata from server to client (for client-streaming calls)" in {
      val trailer = Promise[TrailerFrame]() // control the sending of the trailer

      implicit val serializer: ScalapbProtobufSerializer[HelloReply] = GreeterService.Serializers.HelloReplySerializer
      val metadataServer =
        Http()
          .newServerAt("localhost", 0)
          .bind(path(GreeterService.name / "ItKeepsTalking") {
            implicit val writer: GrpcProtocol.GrpcProtocolWriter = GrpcProtocolNative.newWriter(Identity)
            complete(
              GrpcResponseHelpers(Source.single(HelloReply("Hello there!")), trail = Source.future(trailer.future))
                .addHeader(RawHeader("foo", "bar")))
          })
          .futureValue

      client = GreeterServiceClient(
        GrpcClientSettings.connectToServiceAt("localhost", metadataServer.localAddress.getPort).withTls(false))

      val response = client.itKeepsTalking().invokeWithMetadata(Source.empty).futureValue

      response.value.message shouldBe "Hello there!"
      response.headers.getText("foo") shouldBe Some("bar")

      // only complete trailer after response received, to test reading of trailing headers
      trailer.success(GrpcEntityHelpers.trailer(Status.OK, new HeaderMetadataImpl(List(RawHeader("baz", "qux")))))

      response.trailers.futureValue.getText("baz") shouldBe Some("qux")

      metadataServer.terminate(3.seconds)
    }
  }
}
