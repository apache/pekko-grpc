/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package example.myapp.helloworld;

import example.myapp.helloworld.grpc.GreeterServiceClient;
import example.myapp.helloworld.grpc.HelloReply;
import example.myapp.helloworld.grpc.HelloRequest;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.grpc.GrpcClientSettings;

public class LoggingErrorHandlingGreeterClient {

  public static void main(String[] args) {

    // Boot pekko
    ActorSystem sys = ActorSystem.create("LoggingErrorHandlingGreeterClient");

    // Take details how to connect to the service from the config.
    GrpcClientSettings clientSettings =
        GrpcClientSettings.connectToServiceAt("127.0.0.1", 8082, sys).withTls(false);
    // Create a client-side stub for the service
    GreeterServiceClient client = GreeterServiceClient.create(clientSettings, sys);

    try {
      // #client-calls

      HelloRequest Martin = HelloRequest.newBuilder().setName("Martin").build();
      CompletionStage<HelloReply> successful = client.sayHello(Martin);
      successful.toCompletableFuture().get(10, TimeUnit.SECONDS);
      sys.log().info("Call succeeded");

      HelloRequest martin = HelloRequest.newBuilder().setName("martin").build();
      CompletionStage<HelloReply> failedBecauseLowercase = client.sayHello(martin);
      failedBecauseLowercase
          .handle(
              (response, exception) -> {
                if (exception != null) {
                  sys.log().info("Call with lowercase name failed");
                }
                return response;
              })
          .toCompletableFuture()
          .get(10, TimeUnit.SECONDS);

      HelloRequest empty = HelloRequest.newBuilder().setName("").build();
      CompletionStage<HelloReply> failedBecauseEmpty = client.sayHello(empty);
      failedBecauseEmpty
          .handle(
              (response, exception) -> {
                if (exception != null) {
                  sys.log().info("Call with empty name failed");
                }
                return response;
              })
          .toCompletableFuture()
          .get(10, TimeUnit.SECONDS);

      // #client-calls
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
    sys.terminate();
  }
}
