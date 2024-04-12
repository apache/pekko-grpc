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

// #full-service-impl
package example.myapp.helloworld;

import com.google.protobuf.Timestamp;
import example.myapp.helloworld.grpc.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

public class GreeterServiceImpl implements GreeterService {
  private final Materializer mat;

  public GreeterServiceImpl(Materializer mat) {
    this.mat = mat;
  }

  @Override
  public CompletionStage<HelloReply> sayHello(HelloRequest in) {
    System.out.println("sayHello to " + in.getName());
    HelloReply reply =
        HelloReply.newBuilder()
            .setMessage("Hello, " + in.getName())
            .setTimestamp(Timestamp.newBuilder().setSeconds(1234567890).setNanos(12345).build())
            .build();
    return CompletableFuture.completedFuture(reply);
  }

  @Override
  public CompletionStage<HelloReply> itKeepsTalking(Source<HelloRequest, NotUsed> in) {
    System.out.println("sayHello to in stream...");
    return in.runWith(Sink.<HelloRequest>seq(), mat)
        .thenApply(
            elements -> {
              String elementsStr =
                  elements.stream()
                      .map(HelloRequest::getName)
                      .collect(Collectors.toList())
                      .toString();
              return HelloReply.newBuilder().setMessage("Hello, " + elementsStr).build();
            });
  }

  @Override
  public Source<HelloReply, NotUsed> itKeepsReplying(HelloRequest in) {
    System.out.println("sayHello to " + in.getName() + " with stream of chars");
    List<Character> characters =
        ("Hello, " + in.getName()).chars().mapToObj(c -> (char) c).collect(Collectors.toList());
    return Source.from(characters)
        .map(character -> HelloReply.newBuilder().setMessage(String.valueOf(character)).build());
  }

  @Override
  public Source<HelloReply, NotUsed> streamHellos(Source<HelloRequest, NotUsed> in) {
    System.out.println("sayHello to stream...");
    return in.map(
        request -> HelloReply.newBuilder().setMessage("Hello, " + request.getName()).build());
  }
}
// #full-service-impl
