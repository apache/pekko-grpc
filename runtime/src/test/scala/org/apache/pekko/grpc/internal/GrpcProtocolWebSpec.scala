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

package org.apache.pekko.grpc.internal

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.grpc.GrpcProtocol.{ DataFrame, Frame, TrailerFrame }
import pekko.http.scaladsl.model.HttpHeader
import pekko.http.scaladsl.model.headers.RawHeader
import pekko.stream.scaladsl.Source
import pekko.stream.testkit.scaladsl.TestSink
import pekko.testkit.TestKit
import pekko.util.ByteString
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class GrpcProtocolWebSpec extends TestKit(ActorSystem()) with AnyWordSpecLike with Matchers {

  val reader = GrpcProtocolWeb.newReader(Identity)
  val writer = GrpcProtocolWeb.newWriter(Identity)

  "GrpcProtocolWeb" should {

    "encode and decode a data frame" in {
      val data = ByteString(Array[Byte](1, 2, 3, 4))
      val frame = DataFrame(data)
      val chunk = writer.encodeFrame(frame)

      Source
        .single(chunk.data)
        .via(reader.frameDecoder)
        .runWith(TestSink[Frame]())
        .request(1)
        .expectNext(frame)
        .expectComplete()
    }

    "encode and decode a trailer frame" in {
      val trailers = List[HttpHeader](
        RawHeader("grpc-status", "0"),
        RawHeader("grpc-message", ""))
      val frame = TrailerFrame(trailers)
      val chunk = writer.encodeFrame(frame)

      val probe = Source
        .single(chunk.data)
        .via(reader.frameDecoder)
        .runWith(TestSink[Frame]())
        .request(1)

      probe.expectNext() match {
        case TrailerFrame(decoded) =>
          (decoded should have).length(2)
          decoded.head shouldBe RawHeader("grpc-status", "0")
          decoded(1) shouldBe RawHeader("grpc-message", "")
        case other => fail(s"Expected TrailerFrame, got $other")
      }
      probe.expectComplete()
    }

    "distinguish data frames from trailer frames by type bit" in {
      val data = ByteString(Array[Byte](1, 2, 3))
      val dataFrame = DataFrame(data)
      val trailers = List[HttpHeader](RawHeader("grpc-status", "0"))
      val trailerFrame = TrailerFrame(trailers)

      val encodedData = writer.encodeFrame(dataFrame)
      val encodedTrailer = writer.encodeFrame(trailerFrame)

      val probe = Source(List(encodedData.data, encodedTrailer.data))
        .via(reader.frameDecoder)
        .runWith(TestSink[Frame]())
        .request(2)

      probe.expectNext(dataFrame)
      probe.expectNext() match {
        case TrailerFrame(decoded) =>
          (decoded should have).length(1)
          decoded.head shouldBe RawHeader("grpc-status", "0")
        case other => fail(s"Expected TrailerFrame, got $other")
      }
      probe.expectComplete()
    }

    "reject frame with negative length" in {
      // Construct a raw frame with a negative length (high bit set in length field)
      // Frame format: 1 byte flags + 4 bytes length (big-endian) + data
      val header = new Array[Byte](5)
      header(0) = 0x00 // data frame, no compression
      header(1) = 0x80.toByte // length byte 0 (sets sign bit -> negative int)
      header(2) = 0x00
      header(3) = 0x00
      header(4) = 0x00
      val rawFrame = ByteString.fromArrayUnsafe(header, 0, 5)

      Source
        .single(rawFrame)
        .via(reader.frameDecoder)
        .runWith(TestSink[Frame]())
        .request(1)
        .expectError()
    }
  }
}
