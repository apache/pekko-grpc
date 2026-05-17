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

package example.myapp.statefulhelloworld

import example.myapp.statefulhelloworld.grpc.GreeterService
import example.myapp.statefulhelloworld.grpc.{ ChangeRequest, ChangeResponse, HelloReply, HelloRequest }
import org.apache.pekko
import pekko.actor.typed.{ ActorRef, ActorSystem }
import pekko.actor.typed.scaladsl.AskPattern._
import pekko.util.Timeout

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

// #stateful-service
class GreeterServiceImpl(greeterActor: ActorRef[GreeterActor.GreetingCommand])(implicit system: ActorSystem[_]) extends GreeterService {
  private implicit val timeout: Timeout = 3.seconds
  private implicit val ec: ExecutionContext = system.executionContext

  def sayHello(in: HelloRequest): Future[HelloReply] = {
    greeterActor.ask((replyTo: ActorRef[GreeterActor.Greeting]) => GreeterActor.GetGreeting(replyTo))
      .map(message => HelloReply(s"${message.greeting}, ${in.name}"))
  }

  def changeGreeting(in: ChangeRequest): Future[ChangeResponse] = {
    greeterActor ! GreeterActor.ChangeGreeting(in.newGreeting)
    Future.successful(ChangeResponse())
  }
}
// #stateful-service
