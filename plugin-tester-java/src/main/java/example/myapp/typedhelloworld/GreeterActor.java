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

package example.myapp.typedhelloworld;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

// #actor
public class GreeterActor extends AbstractBehavior<GreeterActor.GreetingCommand> {

  public static interface GreetingCommand {}

  public record ChangeGreeting(String newGreeting) implements GreetingCommand {}

  public record GetGreeting(ActorRef<Greeting> replyTo) implements GreetingCommand {}

  public record Greeting(String greeting) {}

  public static Behavior<GreetingCommand> create(final String initialGreeting) {
    return Behaviors.setup(context -> new GreeterActor(context, initialGreeting));
  }

  private GreeterActor(ActorContext<GreetingCommand> context, String initialGreeting) {
    super(context);
  }

  private Greeting greeting;

  public Receive<GreetingCommand> createReceive() {
    return newReceiveBuilder()
        .onMessage(GetGreeting.class, this::onGetGreeting)
        .onMessage(ChangeGreeting.class, this::onChangeGreeting)
        .build();
  }

  private Behavior<GreetingCommand> onGetGreeting(GetGreeting get) {
    get.replyTo().tell(greeting);
    return this;
  }

  private Behavior<GreetingCommand> onChangeGreeting(ChangeGreeting change) {
    greeting = new Greeting(change.newGreeting());
    return this;
  }
}
// #actor
