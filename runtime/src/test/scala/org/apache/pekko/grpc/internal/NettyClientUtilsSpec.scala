/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.internal

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.util.ByteString
import org.scalatest._
import org.scalatest.concurrent._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters._

class NettyClientUtilsSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {
  implicit val system: ActorSystem = ActorSystem(
    "test",
    ConfigFactory
      .parseString("""
      pekko.discovery.method = alwaystimingout

      pekko.discovery.alwaystimingout.class = org.apache.pekko.grpc.internal.AlwaysTimingOutDiscovery
      """)
      .withFallback(ConfigFactory.load()))

  "The Netty client-utilities" should {
    "accept and advertise the same response encodings as the pekko-http client" in {
      NettyClientUtils.decompressorRegistry.getAdvertisedMessageEncodings.asScala.toSeq should be(
        Codecs.supportedCodecs.filterNot(_ == Identity).map(_.name))
      NettyClientUtils.decompressorRegistry.lookupDecompressor("deflate") should be(NettyClientUtils.DeflateCodec)
    }

    "decompress a deflate message" in {
      val message = ByteString("Hello, Alice " * 100)
      val in = NettyClientUtils.DeflateCodec.decompress(Deflate.compress(message).iterator.asInputStream)
      try ByteString(in.readAllBytes()) should be(message)
      finally in.close()
    }

//    The channel can now retry service discovery as needed itself,
//    I guess we should test that instead?
//    "fail to create a channel when service discovery times out" in {
//      val settings = GrpcClientSettings.usingServiceDiscovery("testService")
//
//      val channel = NettyClientUtils.createChannel(settings)
//    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }
}
