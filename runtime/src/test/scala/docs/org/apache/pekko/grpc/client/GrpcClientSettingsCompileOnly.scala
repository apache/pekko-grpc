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

package docs.org.apache.pekko.grpc.client

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.discovery.{ Discovery, ServiceDiscovery }
import pekko.grpc.GrpcClientSettings

import scala.concurrent.duration._

object GrpcClientSettingsCompileOnly {
  implicit val actorSystem: ActorSystem = ActorSystem()
  // #simple
  GrpcClientSettings.connectToServiceAt("localhost", 443)
  // #simple

  // #simple-programmatic
  GrpcClientSettings.connectToServiceAt("localhost", 443).withDeadline(1.second).withTls(false)
  // #simple-programmatic

  val serviceDiscovery: ServiceDiscovery = Discovery.get(actorSystem).discovery

  // #provide-sd
  // An ActorSystem's default service discovery mechanism
  GrpcClientSettings
    .usingServiceDiscovery(serviceName = "my-service")
    .withServicePortName("https") // (optional) refine the lookup operation to only https ports
  // #provide-sd
}
