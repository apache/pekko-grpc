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

package org.apache.pekko.grpc.interop;

import io.grpc.internal.testing.TestUtils;
import io.grpc.testing.integration2.ClientTester;
import io.grpc.testing.integration2.Settings;
import io.grpc.testing.integration2.TestServiceClient;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.ActorSystem;
import scala.Function2;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

public class PekkoGrpcClientJava extends GrpcClient {

  private final Function2<Settings, ActorSystem, ClientTester> clientTesterFactory;

  public PekkoGrpcClientJava(Function2<Settings, ActorSystem, ClientTester> clientTesterFactory) {
    this.clientTesterFactory = clientTesterFactory;
  }

  public void run(String[] args) {
    TestUtils.installConscryptIfAvailable();
    final Settings settings = Settings.parseArgs(args);

    final ActorSystem sys = ActorSystem.create("PekkoGrpcClientJava");

    final TestServiceClient client =
        new TestServiceClient(clientTesterFactory.apply(settings, sys));
    client.setUp();

    try {
      client.run(settings);
    } finally {
      client.tearDown();
      try {
        Await.result(sys.terminate(), Duration.apply(5, TimeUnit.SECONDS));
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    }
  }
}
