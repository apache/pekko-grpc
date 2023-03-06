/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

//#full-service-impl
package example.myapp.helloworld

import scala.concurrent.Future
import org.apache.pekko
import pekko.NotUsed
import pekko.stream.Materializer
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source
import com.google.protobuf.timestamp.Timestamp
import example.myapp.helloworld.grpc._

class GreeterServiceImpl(implicit mat: Materializer) extends GreeterService {
  import mat.executionContext

  override def sayHello(in: HelloRequest): Future[HelloReply] = {
    println(s"sayHello to ${in.name}")
    Future.successful(HelloReply(s"Hello, ${in.name}", Some(Timestamp.apply(123456, 123))))
  }

  override def itKeepsTalking(in: Source[HelloRequest, NotUsed]): Future[HelloReply] = {
    println(s"sayHello to in stream...")
    in.runWith(Sink.seq).map(elements => HelloReply(s"Hello, ${elements.map(_.name).mkString(", ")}"))
  }

  override def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] = {
    println(s"sayHello to ${in.name} with stream of chars...")
    Source(s"Hello, ${in.name}".toList).map(character => HelloReply(character.toString))
  }

  override def streamHellos(in: Source[HelloRequest, NotUsed]): Source[HelloReply, NotUsed] = {
    println(s"sayHello to stream...")
    in.map(request => HelloReply(s"Hello, ${request.name}"))
  }
}
//#full-service-impl
