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

package example.myapp.statefulhelloworld;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.Props;

// #actor
public class GreeterActor extends AbstractActor {

  public static class ChangeGreeting {
    public final String newGreeting;
    public ChangeGreeting(String newGreeting) {
      this.newGreeting = newGreeting;
    }
  }
  public static class GetGreeting {}
  public static GetGreeting GET_GREETING = new GetGreeting();

  public static class Greeting {
    public final String greeting;
    public Greeting(String greeting) {
      this.greeting = greeting;
    }
  }

  public static Props props(final String initialGreeting) {
    return Props.create(GreeterActor.class, () -> new GreeterActor(initialGreeting));
  }

  private Greeting greeting;

  public GreeterActor(String initialGreeting) {
    greeting = new Greeting(initialGreeting);
  }

  public AbstractActor.Receive createReceive() {
    return receiveBuilder()
        .match(GetGreeting.class, this::onGetGreeting)
        .match(ChangeGreeting.class, this::onChangeGreeting)
        .build();
  }

  private void onGetGreeting(GetGreeting get) {
    getSender().tell(greeting, getSelf());
  }

  private void onChangeGreeting(ChangeGreeting change) {
    greeting = new Greeting(change.newGreeting);
  }
}
// #actor
