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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.WrappersProto;
import com.google.protobuf.wrappers.StringValue;
import io.grpc.Status;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.grpc.GrpcClientSettings;
import org.apache.pekko.grpc.GrpcServiceException;
import org.apache.pekko.grpc.ServiceDescription;
import org.apache.pekko.grpc.internal.GrpcProtocolNative;
import org.apache.pekko.grpc.internal.GrpcProtocolWeb;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.model.headers.RawHeader;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.apache.pekko.util.ByteString;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Checks that the Java API accepts collections of narrower types than it declares. */
public class JavaApiParameterTypesTest {

  private static ActorSystem system;

  @BeforeAll
  public static void setup() {
    system = ActorSystem.create("JavaApiParameterTypesTest");
  }

  @AfterAll
  public static void teardown() {
    TestKit.shutdownActorSystem(system);
  }

  private static CompletionStage<HttpResponse> response(int code) {
    return CompletableFuture.completedFuture(
        HttpResponse.create()
            .withStatus(StatusCodes.custom(code, "reason-" + code, "message-" + code)));
  }

  private static final CompletionStage<HttpResponse> notFound =
      CompletableFuture.completedFuture(HttpResponse.create().withStatus(StatusCodes.NOT_FOUND));

  private static int status(CompletionStage<HttpResponse> response) throws Exception {
    return response.toCompletableFuture().get(3, TimeUnit.SECONDS).status().intValue();
  }

  @Test
  public void serviceHandlerAcceptsAnIterable() throws Exception {
    ArrayDeque<Function<HttpRequest, CompletionStage<HttpResponse>>> handlers = new ArrayDeque<>();
    handlers.add(req -> notFound);
    handlers.add(req -> response(231));
    HttpRequest request =
        HttpRequest.create()
            .withEntity(GrpcProtocolNative.contentType(), ByteString.emptyByteString());

    assertEquals(231, status(ServiceHandler.handler(handlers).apply(request)));
    assertEquals(231, status(ServiceHandler.concatOrNotFound(handlers).apply(request)));
    assertEquals(404, status(ServiceHandler.handler(Collections.emptyList()).apply(request)));
  }

  @Test
  public void webHandlerAcceptsAnIterable() throws Exception {
    Set<Function<HttpRequest, CompletionStage<HttpResponse>>> handlers = new LinkedHashSet<>();
    handlers.add(req -> response(232));
    HttpRequest request =
        HttpRequest.create()
            .withEntity(GrpcProtocolWeb.contentType(), ByteString.emptyByteString());

    Function<HttpRequest, CompletionStage<HttpResponse>> handler =
        WebHandler.grpcWebHandler(handlers, system, Materializer.matFromSystem(system));

    assertEquals(232, status(handler.apply(request)));
  }

  @Test
  public void grpcServiceExceptionAcceptsSpecificDetailTypes() throws Exception {
    List<StringValue> details = List.of(StringValue.of("first"), StringValue.of("second"));

    GrpcServiceException exception =
        GrpcServiceException.create(com.google.rpc.Code.INVALID_ARGUMENT, "invalid", details);

    assertEquals(Status.Code.INVALID_ARGUMENT, exception.getStatus().getCode());
    byte[] statusDetails =
        exception.metadata().getBinary("grpc-status-details-bin").get().toArray();
    assertEquals(2, com.google.rpc.Status.parseFrom(statusDetails).getDetailsCount());
  }

  @Test
  public void metadataBuilderAcceptsSpecificHeaderTypes() {
    List<RawHeader> headers = List.of(RawHeader.create("foo", "bar"));

    Metadata metadata = MetadataBuilder.fromHeaders(headers);

    assertEquals("bar", metadata.getText("foo").orElse(null));
  }

  @Test
  public void serverReflectionAcceptsSpecificDescriptionTypes() {
    TestServiceDescription description = new TestServiceDescription();
    Set<TestServiceDescription> descriptions = Set.of(description);

    assertNotNull(ServerReflection.create(descriptions, system));
  }

  @Test
  public void clientSettingsAcceptsJavaResolveTimeout() {
    GrpcClientSettings settings =
        GrpcClientSettings.connectToServiceAt("localhost", 8080, system)
            .withResolveTimeout(Duration.ofMillis(1500));

    assertEquals(1500, settings.resolveTimeout().toMillis());
  }

  private static final class TestServiceDescription implements ServiceDescription {
    @Override
    public String name() {
      return "test.TestService";
    }

    @Override
    public FileDescriptor descriptor() {
      return WrappersProto.getDescriptor();
    }
  }
}
