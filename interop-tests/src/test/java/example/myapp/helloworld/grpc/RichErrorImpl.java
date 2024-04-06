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

import com.google.protobuf.any.Any;
import com.google.rpc.Code;
import com.google.rpc.Status;
import com.google.rpc.error_details.LocalizedMessage;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.Source;

public class RichErrorImpl implements GreeterService {

  // #rich_error_model_unary
  private com.google.protobuf.Any toJavaProto(com.google.protobuf.any.Any scalaPbSource) {
    com.google.protobuf.Any.Builder javaPbOut = com.google.protobuf.Any.newBuilder();
    javaPbOut.setTypeUrl(scalaPbSource.typeUrl());
    javaPbOut.setValue(scalaPbSource.value());
    return javaPbOut.build();
  }

  @Override
  public CompletionStage<HelloReply> sayHello(HelloRequest in) {
    Status status =
        Status.newBuilder()
            .setCode(Code.INVALID_ARGUMENT_VALUE)
            .setMessage("What is wrong?")
            .addDetails(toJavaProto(Any.pack(LocalizedMessage.of("EN", "The password!"))))
            .build();
    StatusRuntimeException statusRuntimeException = StatusProto.toStatusRuntimeException(status);

    CompletableFuture<HelloReply> future = new CompletableFuture<>();
    future.completeExceptionally(statusRuntimeException);
    return future;
  }
  // #rich_error_model_unary

  @Override
  public CompletionStage<HelloReply> itKeepsTalking(Source<HelloRequest, NotUsed> in) {
    return null;
  }

  @Override
  public Source<HelloReply, NotUsed> itKeepsReplying(HelloRequest in) {
    return null;
  }

  @Override
  public Source<HelloReply, NotUsed> streamHellos(Source<HelloRequest, NotUsed> in) {
    return null;
  }
}
