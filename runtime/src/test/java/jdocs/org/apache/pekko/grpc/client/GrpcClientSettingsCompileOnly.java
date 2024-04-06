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

package jdocs.org.apache.pekko.grpc.client;

import java.time.Duration;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.discovery.Discovery;
import org.apache.pekko.discovery.ServiceDiscovery;
import org.apache.pekko.grpc.GrpcClientSettings;

public class GrpcClientSettingsCompileOnly {
  public static void sd() {

    ActorSystem actorSystem = ActorSystem.create();
    // #simple
    GrpcClientSettings.connectToServiceAt("localhost", 443, actorSystem);
    // #simple

    // #simple-programmatic
    GrpcClientSettings.connectToServiceAt("localhost", 443, actorSystem)
        .withDeadline(Duration.ofSeconds(1))
        .withTls(false);
    // #simple-programmatic

    ServiceDiscovery serviceDiscovery = Discovery.get(actorSystem).discovery();

    // #provide-sd
    // An ActorSystem's default service discovery mechanism
    GrpcClientSettings.usingServiceDiscovery("my-service", actorSystem)
        .withServicePortName("https"); // (optional) refine the lookup operation to only https ports
    // #provide-sd

    // #sd-settings
    // an ActorSystem is required for service discovery
    GrpcClientSettings.fromConfig("project.WithConfigServiceDiscovery", actorSystem);
    // #sd-settings

  }
}
