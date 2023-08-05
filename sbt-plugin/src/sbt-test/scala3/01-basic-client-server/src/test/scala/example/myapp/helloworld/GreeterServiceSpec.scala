/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package example.myapp.helloworld;

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.http.scaladsl.Http

import pekko.grpc.GrpcClientSettings

import example.myapp.helloworld.grpc._

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{ Millis, Seconds, Span }
import org.scalatest.wordspec.AnyWordSpec

class GreeterServiceSpec extends AnyWordSpec with Matchers with ScalaFutures:
  implicit val system: ActorSystem = ActorSystem("GreeterServiceSpec")

  val binding = Http()
    .newServerAt("localhost", 0)
    .bind(GreeterServiceHandler(new GreeterServiceImpl()))
    .futureValue

  val client = GreeterServiceClient(
    GrpcClientSettings.connectToServiceAt(
      "localhost",
      binding.localAddress.getPort).withTls(false))

  "A GreeterService" should {
    "respond to a unary request" in {
      val reply = client.sayHello(HelloRequest("Dave"))
      val r = scala.concurrent.Await.result(reply, 10.seconds)
      r.message shouldBe "Hello, Dave!"
    }
  }
