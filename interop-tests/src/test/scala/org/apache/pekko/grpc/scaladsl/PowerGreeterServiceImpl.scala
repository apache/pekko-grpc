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

package org.apache.pekko.grpc.scaladsl

import scala.concurrent.Future

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source
import example.myapp.helloworld.grpc.helloworld._

class PowerGreeterServiceImpl()(implicit system: ActorSystem) extends GreeterServicePowerApi {
  import system.dispatcher

  override def sayHello(in: HelloRequest, metadata: Metadata): Future[HelloReply] = {
    val greetee = authTaggedName(in, metadata)
    println(s"sayHello to $greetee")
    Future.successful(HelloReply(s"Hello, $greetee"))
  }

  override def itKeepsTalking(in: Source[HelloRequest, NotUsed], metadata: Metadata): Future[HelloReply] = {
    println(s"sayHello to in stream...")
    in.runWith(Sink.seq)
      .map(elements => HelloReply(s"Hello, ${elements.map(authTaggedName(_, metadata)).mkString(", ")}"))
  }

  override def itKeepsReplying(in: HelloRequest, metadata: Metadata): Source[HelloReply, NotUsed] = {
    val greetee = authTaggedName(in, metadata)
    println(s"sayHello to $greetee with stream of chars...")
    Source(s"Hello, $greetee".toList).map(character => HelloReply(character.toString))
  }

  /**
   * Once a HelloRequest is received, replies with a never-ending stream of HelloReply's until
   * a HelloRequest("ByeBye") is received.
   */
  override def streamHellos(in: Source[HelloRequest, NotUsed], metadata: Metadata): Source[HelloReply, NotUsed] = {
    println(s"sayHello to stream...")

    val expandedSource = in.expand { req =>
      if (req.name == "ByeBye") {
        // Sending HelloRequest("ByeBye") causes the server to respond with one last HelloResponse("ByeBye") so
        // the client should at least see that one.
        Iterator.single(req)
      } else
        Iterator.continually(req)
    }

    expandedSource.map(request => HelloReply(s"Hello, ${authTaggedName(request, metadata)}"))
  }

  // Bare-bones just for GRPC metadata demonstration purposes
  private def isAuthenticated(metadata: Metadata): Boolean =
    metadata.getText("authorization").nonEmpty

  private def authTaggedName(in: HelloRequest, metadata: Metadata): String = {
    val authenticated = isAuthenticated(metadata)
    s"${in.name} (${if (!authenticated) "not " else ""}authenticated)"
  }
}
