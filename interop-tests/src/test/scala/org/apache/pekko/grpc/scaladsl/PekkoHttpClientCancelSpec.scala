/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.scaladsl

import org.apache.pekko
import pekko.Done
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.grpc.GrpcClientSettings
import pekko.http.scaladsl.Http
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source
import pekko.testkit.TestKit
import pekko.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import example.myapp.helloworld.grpc.helloworld._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Success

class PekkoHttpClientCancelSpec
    extends TestKit(
      ActorSystem(
        "PekkoHttpClientCancelSpec",
        ConfigFactory.parseString("pekko.http.server.enable-http2 = on").withFallback(ConfigFactory.load())))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures {

  implicit val ec: ExecutionContext = system.dispatcher

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  "The Pekko HTTP client" should {

    "correctly cancel a server streaming call" in {
      val probe = TestProbe()

      val handler = GreeterServiceHandler(new GreeterService {
        override def sayHello(in: HelloRequest): Future[HelloReply] = ???
        override def itKeepsTalking(in: Source[HelloRequest, NotUsed]): Future[HelloReply] = ???
        override def streamHellos(in: Source[HelloRequest, NotUsed]): Source[HelloReply, NotUsed] = ???

        override def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] = {
          Source
            .single(HelloReply.defaultInstance)
            // keep the stream alive indefinitely
            .concat(Source.maybe[HelloReply])
            // tell probe when we start and when we complete
            .watchTermination { (_, termination) =>
              probe.ref ! "started"
              termination.onComplete { t =>
                probe.ref ! t
              }
              NotUsed
            }
        }
      })

      val binding =
        Http().newServerAt("127.0.0.1", 0).bind(handler).futureValue

      val client =
        GreeterServiceClient(
          GrpcClientSettings
            .connectToServiceAt("127.0.0.1", binding.localAddress.getPort)
            .withTls(false)
            .withBackend("pekko-http"))
      client.itKeepsReplying(HelloRequest.defaultInstance).runWith(Sink.head)

      probe.expectMsg("started")
      probe.expectMsg(5.seconds, Success(Done))

      client.close().futureValue
      binding.unbind().futureValue
    }
  }
}
