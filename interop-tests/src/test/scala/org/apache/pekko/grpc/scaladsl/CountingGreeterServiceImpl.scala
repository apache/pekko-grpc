/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.scaladsl

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source

import example.myapp.helloworld.grpc.helloworld._

class CountingGreeterServiceImpl extends GreeterService {
  var greetings = new AtomicInteger(0);

  def sayHello(in: HelloRequest): Future[HelloReply] = {
    greetings.incrementAndGet()
    Future.successful(HelloReply(s"Hi ${in.name}!"))
  }

  def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] =
    Source(List(HelloReply("First"), HelloReply("Second"))).mapMaterializedValue { m => println("XXX MAT YYY"); m }
  def itKeepsTalking(
      in: org.apache.pekko.stream.scaladsl.Source[example.myapp.helloworld.grpc.helloworld.HelloRequest,
        org.apache.pekko.NotUsed]): scala.concurrent.Future[example.myapp.helloworld.grpc.helloworld.HelloReply] = ???
  def streamHellos(
      in: org.apache.pekko.stream.scaladsl.Source[example.myapp.helloworld.grpc.helloworld.HelloRequest,
        org.apache.pekko.NotUsed]): org.apache.pekko.stream.scaladsl.Source[
    example.myapp.helloworld.grpc.helloworld.HelloReply, org.apache.pekko.NotUsed] = ???

}
