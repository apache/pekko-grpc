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

package example.myapp.helloworld.grpc;

import static org.junit.Assert.assertEquals;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.grpc.GrpcClientSettings;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.junit.Assert;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

public class RichErrorModelTest extends JUnitSuite {

  private com.google.protobuf.any.Any fromJavaProto(com.google.protobuf.Any javaPbSource) {
    return com.google.protobuf.any.Any.of(javaPbSource.getTypeUrl(), javaPbSource.getValue());
  }

  private CompletionStage<ServerBinding> run(ActorSystem sys) throws Exception {

    GreeterService impl = new RichErrorImpl();

    org.apache.pekko.japi.function.Function<HttpRequest, CompletionStage<HttpResponse>> service =
        GreeterServiceHandlerFactory.create(impl, sys);

    return Http.get(sys).newServerAt("127.0.0.1", 8090).bind(service);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testManualApproach() throws Exception {
    Config conf = ConfigFactory.load();
    ActorSystem sys = ActorSystem.create("HelloWorld", conf);
    run(sys);

    GrpcClientSettings settings =
        GrpcClientSettings.connectToServiceAt("127.0.0.1", 8090, sys).withTls(false);

    GreeterServiceClient client = null;
    try {
      client = GreeterServiceClient.create(settings, sys);

      // #client_request
      HelloRequest request = HelloRequest.newBuilder().setName("Alice").build();
      CompletionStage<HelloReply> response = client.sayHello(request);
      StatusRuntimeException statusEx =
          response
              .toCompletableFuture()
              .handle(
                  (res, ex) -> {
                    return (StatusRuntimeException) ex;
                  })
              .get();

      com.google.rpc.Status status =
          StatusProto.fromStatusAndTrailers(statusEx.getStatus(), statusEx.getTrailers());

      assertEquals(
          "type.googleapis.com/google.rpc.LocalizedMessage", status.getDetails(0).getTypeUrl());

      com.google.rpc.error_details.LocalizedMessage details =
          fromJavaProto(status.getDetails(0))
              .unpack(com.google.rpc.error_details.LocalizedMessage.messageCompanion());

      assertEquals(Status.INVALID_ARGUMENT.getCode().value(), status.getCode());
      assertEquals("What is wrong?", status.getMessage());
      assertEquals("The password!", details.message());
      assertEquals("EN", details.locale());
      // #client_request

    } catch (Exception e) {
      Assert.fail("Got unexpected error " + e.getMessage());
    } finally {
      if (client != null) client.close();
      sys.terminate();
    }
  }
}
