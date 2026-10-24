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

package example.myapp.helloworld.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.grpc.GrpcClientSettings;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.stream.javadsl.Source;
import org.junit.jupiter.api.Test;

public class PerCallDeadlineTest {

  /** A service that never replies */
  private static class NeverReplyingGreeterService implements GreeterService {
    @Override
    public CompletionStage<HelloReply> sayHello(HelloRequest in) {
      return new CompletableFuture<>();
    }

    @Override
    public CompletionStage<HelloReply> itKeepsTalking(Source<HelloRequest, NotUsed> in) {
      return new CompletableFuture<>();
    }

    @Override
    public Source<HelloReply, NotUsed> itKeepsReplying(HelloRequest in) {
      return Source.<HelloReply>maybe().mapMaterializedValue(p -> NotUsed.getInstance());
    }

    @Override
    public Source<HelloReply, NotUsed> streamHellos(Source<HelloRequest, NotUsed> in) {
      return Source.<HelloReply>maybe().mapMaterializedValue(p -> NotUsed.getInstance());
    }
  }

  @Test
  public void failUnaryCallWhenPerCallDeadlineIsExceededNetty() throws Exception {
    failUnaryCallWhenPerCallDeadlineIsExceeded("netty");
  }

  @Test
  public void failUnaryCallWhenPerCallDeadlineIsExceededPekkoHttp() throws Exception {
    failUnaryCallWhenPerCallDeadlineIsExceeded("pekko-http");
  }

  private void failUnaryCallWhenPerCallDeadlineIsExceeded(String backend) throws Exception {
    ActorSystem sys = ActorSystem.create("PerCallDeadlineTest");
    ServerBinding binding =
        Http.get(sys)
            .newServerAt("127.0.0.1", 0)
            .bind(GreeterServiceHandlerFactory.create(new NeverReplyingGreeterService(), sys))
            .toCompletableFuture()
            .get(10, TimeUnit.SECONDS);
    GreeterServiceClient client =
        GreeterServiceClient.create(
            GrpcClientSettings.connectToServiceAt(
                    "127.0.0.1", binding.localAddress().getPort(), sys)
                .withTls(false)
                .withBackend(backend),
            sys);
    try {
      HelloRequest request = HelloRequest.newBuilder().setName("Alice").build();
      // #per-call-deadline
      CompletionStage<HelloReply> reply =
          client.sayHello().setDeadline(Duration.ofMillis(200)).invoke(request);
      // #per-call-deadline

      ExecutionException failure =
          assertThrows(
              ExecutionException.class,
              () -> reply.toCompletableFuture().get(10, TimeUnit.SECONDS));
      assertInstanceOf(StatusRuntimeException.class, failure.getCause());
      assertEquals(
          Status.Code.DEADLINE_EXCEEDED,
          ((StatusRuntimeException) failure.getCause()).getStatus().getCode());
    } finally {
      client.close().toCompletableFuture().get(10, TimeUnit.SECONDS);
      binding.unbind().toCompletableFuture().get(10, TimeUnit.SECONDS);
      sys.terminate();
    }
  }
}
