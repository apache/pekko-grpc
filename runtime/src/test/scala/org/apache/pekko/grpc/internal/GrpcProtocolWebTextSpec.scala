/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.internal

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.grpc.GrpcProtocol.{ DataFrame, Frame }
import pekko.stream.scaladsl.Source
import pekko.stream.testkit.scaladsl.TestSink
import pekko.testkit.TestKit
import pekko.util.ByteString
import org.scalatest.wordspec.AnyWordSpecLike

class GrpcProtocolWebTextSpec extends TestKit(ActorSystem()) with AnyWordSpecLike {

  "GrpcProtocolWebText" should {
    val reader = GrpcProtocolWebText.newReader(Identity)
    val writer = GrpcProtocolWebText.newWriter(Identity)

    val data = ByteString(Range(-128, 128).map(_.toByte).toArray)
    val frame = DataFrame(data)
    val chunk = writer.encodeFrame(frame)

    "decode a full frame" in {
      Source
        .single(chunk.data)
        .via(reader.frameDecoder)
        .runWith(TestSink[Frame]())
        .request(1)
        .expectNext(frame)
        .expectComplete()
    }

    "decode a fragmented frame" in {
      for (i <- Range(1, 8)) {
        Source(chunk.data.grouped(i).toList)
          .via(reader.frameDecoder)
          .runWith(TestSink[Frame]())
          .request(1)
          .expectNext(frame)
          .expectComplete()
      }
    }
  }
}
