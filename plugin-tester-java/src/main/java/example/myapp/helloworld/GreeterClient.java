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

// #full-client
package example.myapp.helloworld;

import example.myapp.helloworld.grpc.*;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.grpc.GrpcClientSettings;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Source;

class GreeterClient {
  public static void main(String[] args) throws Exception {

    String serverHost = "127.0.0.1";
    int serverPort = 8090;

    ActorSystem system = ActorSystem.create("HelloWorldClient");
    Materializer materializer = SystemMaterializer.get(system).materializer();

    // Configure the client by code:
    GrpcClientSettings settings =
        GrpcClientSettings.connectToServiceAt("127.0.0.1", 8090, system).withTls(false);

    // Or via application.conf:
    // GrpcClientSettings settings = GrpcClientSettings.fromConfig(GreeterService.name, system);

    GreeterServiceClient client = null;
    try {
      client = GreeterServiceClient.create(settings, system);

      singleRequestReply(client);
      streamingRequest(client);
      streamingReply(client, materializer);
      streamingRequestReply(client, materializer);

    } catch (StatusRuntimeException e) {
      System.out.println("Status: " + e.getStatus());
    } catch (Exception e) {
      System.out.println(e);
    } finally {
      if (client != null) client.close();
      system.terminate();
    }
  }

  private static void singleRequestReply(GreeterService client) throws Exception {
    HelloRequest request = HelloRequest.newBuilder().setName("Alice").build();
    CompletionStage<HelloReply> reply = client.sayHello(request);
    System.out.println("got single reply: " + reply.toCompletableFuture().get(5, TimeUnit.SECONDS));
  }

  private static void streamingRequest(GreeterService client) throws Exception {
    List<HelloRequest> requests =
        Arrays.asList("Alice", "Bob", "Peter").stream()
            .map(name -> HelloRequest.newBuilder().setName(name).build())
            .collect(Collectors.toList());
    CompletionStage<HelloReply> reply = client.itKeepsTalking(Source.from(requests));
    System.out.println(
        "got single reply for streaming requests: "
            + reply.toCompletableFuture().get(5, TimeUnit.SECONDS));
  }

  private static void streamingReply(GreeterService client, Materializer mat) throws Exception {
    HelloRequest request = HelloRequest.newBuilder().setName("Alice").build();
    Source<HelloReply, NotUsed> responseStream = client.itKeepsReplying(request);
    CompletionStage<Done> done =
        responseStream.runForeach(
            reply -> System.out.println("got streaming reply: " + reply.getMessage()), mat);

    done.toCompletableFuture().get(60, TimeUnit.SECONDS);
  }

  private static void streamingRequestReply(GreeterService client, Materializer mat)
      throws Exception {
    Duration interval = Duration.ofSeconds(1);
    Source<HelloRequest, NotUsed> requestStream =
        Source.tick(interval, interval, "tick")
            .zipWithIndex()
            .map(pair -> pair.second())
            .map(i -> HelloRequest.newBuilder().setName("Alice-" + i).build())
            .take(10)
            .mapMaterializedValue(m -> NotUsed.getInstance());

    Source<HelloReply, NotUsed> responseStream = client.streamHellos(requestStream);
    CompletionStage<Done> done =
        responseStream.runForeach(
            reply -> System.out.println("got streaming reply: " + reply.getMessage()), mat);

    done.toCompletableFuture().get(60, TimeUnit.SECONDS);
  }
}
// #full-client
