/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

import org.apache.pekko
import pekko.Done
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.grpc.GrpcClientSettings
import pekko.stream.scaladsl.Source

import example.myapp.helloworld.grpc._

object LiftedGreeterClient {
  def main(args: Array[String]): Unit = {
    implicit val sys: ActorSystem = ActorSystem("HelloWorldClient")
    implicit val ec: ExecutionContext = sys.dispatcher

    val clientSettings = GrpcClientSettings.fromConfig(GreeterService.name)
    val client = GreeterServiceClient(clientSettings)

    singleRequestReply()
    streamingRequest()
    streamingReply()
    streamingRequestReply()

    sys.scheduler.scheduleWithFixedDelay(1.second, 1.second) { () => Try(singleRequestReply()) }

    // #with-metadata
    def singleRequestReply(): Unit = {
      sys.log.info("Performing request")
      val reply = client.sayHello().addHeader("key", "value").invoke(HelloRequest("Alice"))
      println(s"got single reply: ${Await.result(reply, 5.seconds).message}")
    }
    // #with-metadata

    def streamingRequest(): Unit = {
      val requests = List("Alice", "Bob", "Peter").map(HelloRequest(_))
      val reply = client.itKeepsTalking().addHeader("key", "value").invoke(Source(requests))
      println(s"got single reply for streaming requests: ${Await.result(reply, 5.seconds).message}")
    }

    def streamingReply(): Unit = {
      val responseStream = client.itKeepsReplying().addHeader("key", "value").invoke(HelloRequest("Alice"))
      val done: Future[Done] =
        responseStream.runForeach(reply => println(s"got streaming reply: ${reply.message}"))
      Await.ready(done, 1.minute)
    }

    def streamingRequestReply(): Unit = {
      val requestStream: Source[HelloRequest, NotUsed] =
        Source
          .tick(100.millis, 1.second, "tick")
          .zipWithIndex
          .map { case (_, i) => i }
          .map(i => HelloRequest(s"Alice-$i"))
          .take(10)
          .mapMaterializedValue(_ => NotUsed)

      val responseStream: Source[HelloReply, NotUsed] =
        client.streamHellos().addHeader("key", "value").invoke(requestStream)
      val done: Future[Done] =
        responseStream.runForeach(reply => println(s"got streaming reply: ${reply.message}"))
      Await.ready(done, 1.minute)
    }
  }
}
