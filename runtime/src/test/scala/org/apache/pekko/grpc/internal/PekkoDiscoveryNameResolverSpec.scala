/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.internal

import java.net.InetSocketAddress

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.grpc.{ GrpcClientSettings, GrpcServiceException }
import pekko.testkit.TestKit
import io.grpc.Status
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{ Millis, Seconds, Span }
import org.scalatest.wordspec.AnyWordSpecLike

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

class PekkoDiscoveryNameResolverSpec
    extends TestKit(ActorSystem())
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures {
  implicit val ex: ExecutionContext = system.dispatcher
  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(2, Seconds)), interval = scaled(Span(5, Millis)))

  "The PekkoDiscovery-backed NameResolver" should {
    "correctly report an error for an unknown hostname" in {
      val host = "example.invalid"
      val resolver = PekkoDiscoveryNameResolver(GrpcClientSettings.connectToServiceAt(host, 80))
      val probe = new NameResolverListenerProbe()

      resolver.start(probe)

      val exception = probe.future.failed.futureValue.asInstanceOf[GrpcServiceException]
      exception shouldBe an[GrpcServiceException]
      exception.status.getCode == Status.UNKNOWN.getCode
      // FIXME: This description is not portable - it arises from native function response, which differs by OS
      // exception.status.getDescription should equal(host + ": Name or service not known")
    }

    "support serving a static host/port" in {
      // Unfortunately it needs to be an actually resolvable address...
      val host = "pekko.io"
      val port = 4040
      val resolver = PekkoDiscoveryNameResolver(GrpcClientSettings.connectToServiceAt(host, port))
      val probe = new NameResolverListenerProbe()

      resolver.start(probe)

      val addresses = probe.future.futureValue match {
        case Seq(addressGroup) => addressGroup.getAddresses
        case _                 => fail("Expected a single address group")
      }
      addresses.asScala.toSeq match {
        case Seq(address: InetSocketAddress) =>
          address.getPort should be(port)
          address.getAddress.getHostName should be(host)
        case other =>
          fail(s"Expected a single InetSocketAddress, got $other")
      }
    }
  }
}
