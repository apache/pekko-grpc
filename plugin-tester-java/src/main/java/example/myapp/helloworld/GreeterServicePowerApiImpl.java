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

import example.myapp.helloworld.grpc.GreeterServicePowerApi;
import example.myapp.helloworld.grpc.HelloReply;
import example.myapp.helloworld.grpc.HelloRequest;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import org.apache.pekko.NotUsed;
import org.apache.pekko.grpc.javadsl.Metadata;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

public class GreeterServicePowerApiImpl implements GreeterServicePowerApi {
  private final Materializer mat;

  public GreeterServicePowerApiImpl(Materializer mat) {
    this.mat = mat;
  }

  @Override
  public CompletionStage<HelloReply> sayHello(HelloRequest in, Metadata metadata) {
    String greetee = authTaggedName(in, metadata);
    System.out.println("sayHello to " + greetee);
    HelloReply reply = HelloReply.newBuilder().setMessage("Hello, " + greetee).build();
    return CompletableFuture.completedFuture(reply);
  }

  @Override
  public CompletionStage<HelloReply> itKeepsTalking(
      Source<HelloRequest, NotUsed> in, Metadata metadata) {
    System.out.println("sayHello to in stream...");
    return in.runWith(Sink.seq(), mat)
        .thenApply(
            elements -> {
              String elementsStr =
                  elements.stream()
                      .map(elem -> authTaggedName(elem, metadata))
                      .collect(Collectors.toList())
                      .toString();
              return HelloReply.newBuilder().setMessage("Hello, " + elementsStr).build();
            });
  }

  @Override
  public Source<HelloReply, NotUsed> itKeepsReplying(HelloRequest in, Metadata metadata) {
    String greetee = authTaggedName(in, metadata);
    System.out.println("sayHello to " + greetee + " with stream of chars");
    List<Character> characters =
        ("Hello, " + greetee).chars().mapToObj(c -> (char) c).collect(Collectors.toList());
    return Source.from(characters)
        .map(
            character -> {
              return HelloReply.newBuilder().setMessage(String.valueOf(character)).build();
            });
  }

  @Override
  public Source<HelloReply, NotUsed> streamHellos(
      Source<HelloRequest, NotUsed> in, Metadata metadata) {
    System.out.println("sayHello to stream...");
    return in.map(
        request ->
            HelloReply.newBuilder()
                .setMessage("Hello, " + authTaggedName(request, metadata))
                .build());
  }

  // Bare-bones just for GRPC metadata demonstration purposes
  private boolean isAuthenticated(Metadata metadata) {
    return metadata.getText("authorization").isPresent();
  }

  private String authTaggedName(HelloRequest in, Metadata metadata) {
    boolean authenticated = isAuthenticated(metadata);
    return String.format(
        "%s (%sauthenticated)", in.getName(), isAuthenticated(metadata) ? "" : "not ");
  }
}
// #full-service-impl
