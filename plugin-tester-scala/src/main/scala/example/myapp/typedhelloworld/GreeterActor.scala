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

package example.myapp.typedhelloworld

import org.apache.pekko
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.{ ActorRef, Behavior }

// #actor
object GreeterActor {
  sealed trait GreetingCommand
  case class ChangeGreeting(newGreeting: String) extends GreetingCommand
  case class GetGreeting(replyTo: ActorRef[Greeting]) extends GreetingCommand

  case object GetGreeting
  case class Greeting(greeting: String)

  def apply(initialGreeting: String): Behavior[GreetingCommand] = (new GreeterActor(initialGreeting)).createBehavior()
}

class GreeterActor(initialGreeting: String) {
  import GreeterActor._

  var greeting = Greeting(initialGreeting)

  def createBehavior(): Behavior[GreetingCommand] = Behaviors.receiveMessage {
    case ChangeGreeting(newGreeting) =>
      greeting = Greeting(newGreeting)
      createBehavior()
    case GetGreeting(replyTo) =>
      replyTo ! greeting
      Behaviors.same
  }
}
// #actor
