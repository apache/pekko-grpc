/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.interop

import scala.concurrent.duration._
import org.apache.pekko.actor.ActorSystem
import io.grpc.internal.testing.TestUtils
import io.grpc.testing.integration2.{ ClientTester, Settings, TestServiceClient }

import scala.concurrent.Await

// TODO #151 use our own Settings object
final case class PekkoGrpcClientScala(clientTesterFactory: Settings => ActorSystem => ClientTester) extends GrpcClient {
  override def run(args: Array[String]): Unit = {
    TestUtils.installConscryptIfAvailable()
    val settings = Settings.parseArgs(args)

    implicit val sys: ActorSystem = ActorSystem()

    val client = new TestServiceClient(clientTesterFactory(settings)(sys))
    client.setUp()

    try client.run(settings)
    finally {
      client.tearDown()
      Await.result(sys.terminate(), 10.seconds)
    }
  }
}
