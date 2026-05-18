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


import example.myapp.statefulhelloworld.grpc.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;

// #stateful-service
public final class GreeterServiceImpl implements GreeterService {

  private final ActorSystem system;
  private final ActorRef<GreeterActor.GreetingCommand> greeterActor;

  public GreeterServiceImpl(ActorSystem system, ActorRef<GreeterActor.GreetingCommand> greeterActor) {
    this.system = system;
    this.greeterActor = greeterActor;
  }

  public CompletionStage<HelloReply> sayHello(HelloRequest in) {
    CompletionStage<GreeterActor.Greeting> response = AskPattern.ask(
       greeterActor,
       replyTo -> new GreeterActor.GetGreeting(replyTo),
       Duration.ofSeconds(5),
       system.scheduler()
    );
    return response.thenApply(
            message ->
                HelloReply.newBuilder()
                    .setMessage(((GreeterActor.Greeting) message).greeting)
                    .build()
    );
  }

  public CompletionStage<ChangeResponse> changeGreeting(ChangeRequest in) {
    greeterActor.tell(new GreeterActor.ChangeGreeting(in.getNewGreeting()));
    return CompletableFuture.completedFuture(ChangeResponse.newBuilder().build());
  }
}
// #stateful-service
