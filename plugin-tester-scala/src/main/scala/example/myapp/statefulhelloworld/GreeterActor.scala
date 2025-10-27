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

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.Props

// #actor
object GreeterActor {
  case class ChangeGreeting(newGreeting: String)

  case object GetGreeting
  case class Greeting(greeting: String)

  def props(initialGreeting: String) = Props(new GreeterActor(initialGreeting))
}

class GreeterActor(initialGreeting: String) extends Actor {
  import GreeterActor._

  var greeting = Greeting(initialGreeting)

  def receive = {
    case GetGreeting                 => sender() ! greeting
    case ChangeGreeting(newGreeting) =>
      greeting = Greeting(newGreeting)
  }
}
// #actor
