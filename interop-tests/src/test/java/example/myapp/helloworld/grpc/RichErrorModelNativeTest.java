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

import com.google.rpc.error_details.LocalizedMessage;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.grpc.GrpcClientSettings;
import org.apache.pekko.grpc.GrpcServiceException;
import org.apache.pekko.grpc.javadsl.MetadataStatus;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.junit.Assert;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

public class RichErrorModelNativeTest extends JUnitSuite {

  private ServerBinding run(ActorSystem sys) throws Exception {

    GreeterService impl = new RichErrorNativeImpl();

    org.apache.pekko.japi.function.Function<HttpRequest, CompletionStage<HttpResponse>> service =
        GreeterServiceHandlerFactory.create(impl, sys);
    CompletionStage<ServerBinding> bound =
        Http.get(sys).newServerAt("127.0.0.1", 8091).bind(service);

    bound.thenAccept(
        binding -> {
          System.out.println("gRPC server bound to: " + binding.localAddress());
        });
    return bound.toCompletableFuture().get();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testNativeApi() throws Exception {
    Config conf = ConfigFactory.load();
    ActorSystem sys = ActorSystem.create("HelloWorld", conf);
    run(sys);

    GrpcClientSettings settings =
        GrpcClientSettings.connectToServiceAt("127.0.0.1", 8091, sys).withTls(false);

    GreeterServiceClient client = null;
    try {
      client = GreeterServiceClient.create(settings, sys);

      // #client_request
      HelloRequest request = HelloRequest.newBuilder().setName("Alice").build();
      CompletionStage<HelloReply> response = client.sayHello(request);
      StatusRuntimeException statusRuntimeException =
          response
              .toCompletableFuture()
              .handle(
                  (res, ex) -> {
                    return (StatusRuntimeException) ex;
                  })
              .get();

      GrpcServiceException ex = GrpcServiceException.apply(statusRuntimeException);
      MetadataStatus meta = (MetadataStatus) ex.getMetadata();
      assertEquals(
          "type.googleapis.com/google.rpc.LocalizedMessage", meta.getDetails().get(0).typeUrl());

      assertEquals(Status.INVALID_ARGUMENT.getCode().value(), meta.getCode());
      assertEquals("What is wrong?", meta.getMessage());

      LocalizedMessage details =
          meta.getParsedDetails(com.google.rpc.error_details.LocalizedMessage.messageCompanion())
              .get(0);
      assertEquals("The password!", details.message());
      assertEquals("EN", details.locale());
      // #client_request

    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail("Got unexpected error " + e.getMessage());
    } finally {
      if (client != null) client.close();
      sys.terminate();
    }
  }
}
