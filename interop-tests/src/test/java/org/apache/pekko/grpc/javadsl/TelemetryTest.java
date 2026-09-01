/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.grpc.javadsl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.typesafe.config.ConfigFactory;
import example.myapp.helloworld.grpc.GreeterService;
import example.myapp.helloworld.grpc.GreeterServiceHandlerFactory;
import example.myapp.helloworld.grpc.HelloReply;
import example.myapp.helloworld.grpc.HelloRequest;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.grpc.internal.GrpcProtocolNative;
import org.apache.pekko.grpc.internal.TelemetryExtension;
import org.apache.pekko.http.javadsl.model.HttpMethods;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.apache.pekko.util.ByteString;
import org.junit.jupiter.api.Test;

/** The scaladsl counterpart lives in org.apache.pekko.grpc.scaladsl.TelemetrySpec. */
public class TelemetryTest {

  private static class TestGreeter implements GreeterService {
    @Override
    public CompletionStage<HelloReply> sayHello(HelloRequest in) {
      return CompletableFuture.completedFuture(
          HelloReply.newBuilder().setMessage("Hello, " + in.getName()).build());
    }

    @Override
    public CompletionStage<HelloReply> itKeepsTalking(Source<HelloRequest, NotUsed> in) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Source<HelloReply, NotUsed> itKeepsReplying(HelloRequest in) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Source<HelloReply, NotUsed> streamHellos(Source<HelloRequest, NotUsed> in) {
      throw new UnsupportedOperationException();
    }
  }

  /** A single gRPC data frame: 1 flag byte, then a 4 byte big-endian length, then the message. */
  private static ByteString frame(byte[] message) {
    int length = message.length;
    return ByteString.fromArray(
            new byte[] {
              0,
              (byte) (length >>> 24),
              (byte) (length >>> 16),
              (byte) (length >>> 8),
              (byte) length
            })
        .concat(ByteString.fromArray(message));
  }

  @Test
  public void theGeneratedHandlerReportsMatchedRequests() throws Exception {
    ActorSystem system =
        ActorSystem.create(
            "TelemetryTest",
            ConfigFactory.parseString(
                    "pekko.grpc.telemetry-class ="
                        + " \"org.apache.pekko.grpc.javadsl.CollectingTelemetrySpi\"")
                .withFallback(ConfigFactory.load()));
    try {
      Function<HttpRequest, CompletionStage<HttpResponse>> handler =
          GreeterServiceHandlerFactory.create(new TestGreeter(), system);
      HttpRequest request =
          HttpRequest.create("https://localhost/" + GreeterService.name + "/SayHello")
              .withMethod(HttpMethods.POST)
              .withEntity(
                  GrpcProtocolNative.contentType(),
                  frame(HelloRequest.newBuilder().setName("Joe").build().toByteArray()));

      HttpResponse response =
          handler.apply(request).toCompletableFuture().get(10, TimeUnit.SECONDS);
      assertEquals(StatusCodes.OK, response.status());

      CollectingTelemetrySpi spi = (CollectingTelemetrySpi) TelemetryExtension.get(system).spi();
      assertEquals(1, spi.requests().size());
      CollectingTelemetrySpi.Request collected = spi.requests().get(0);
      assertEquals(GreeterService.name, collected.prefix);
      assertEquals("SayHello", collected.method);
      assertEquals(GrpcProtocolNative.contentType(), collected.request.entity().getContentType());
    } finally {
      TestKit.shutdownActorSystem(system);
    }
  }
}
