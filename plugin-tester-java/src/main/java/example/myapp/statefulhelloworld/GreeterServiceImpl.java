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

import example.myapp.statefulhelloworld.grpc.*;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ActorRef;
import static org.apache.pekko.pattern.Patterns.ask;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

// #stateful-service
public final class GreeterServiceImpl implements GreeterService {

  private final ActorSystem system;
  private final ActorRef greeterActor;

  public GreeterServiceImpl(ActorSystem system) {
    this.system = system;
    this.greeterActor = system.actorOf(GreeterActor.props("Hello"), "greeter");
  }

  public CompletionStage<HelloReply> sayHello(HelloRequest in) {
    return ask(greeterActor, GreeterActor.GET_GREETING, Duration.ofSeconds(5))
        .thenApply(message ->
          HelloReply.newBuilder()
            .setMessage(((GreeterActor.Greeting) message).greeting)
            .build()
        );
  }

  public CompletionStage<ChangeResponse> changeGreeting(ChangeRequest in) {
    greeterActor.tell(new GreeterActor.ChangeGreeting(in.getNewGreeting()), ActorRef.noSender());
    return CompletableFuture.completedFuture(ChangeResponse.newBuilder().build());
  }

}
// #stateful-service
