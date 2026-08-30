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

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.grpc.GrpcProtocol.{ DataFrame, Frame }
import pekko.stream.scaladsl.Source
import pekko.stream.testkit.scaladsl.TestSink
import pekko.testkit.TestKit
import pekko.util.ByteString
import io.grpc.{ Status, StatusException }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class MaxInboundMessageSizeSpec extends TestKit(ActorSystem()) with AnyWordSpecLike with Matchers {

  private val Limit = 64 * 1024

  /** Inflates to far more than `Limit`, but compresses to far less, so only a bounded decompressor catches it. */
  private val BombSize = 4 * 1024 * 1024

  private val TooLargeWhenDecompressed = s"Decompressed message size exceeds maximum allowed $Limit bytes"

  /** A frame header: 1 byte flags + 4 bytes big-endian length. */
  private def frameHeader(flags: Byte, length: Int): ByteString = {
    val header = new Array[Byte](5)
    header(0) = flags
    header(1) = (length >>> 24).toByte
    header(2) = (length >>> 16).toByte
    header(3) = (length >>> 8).toByte
    header(4) = length.toByte
    ByteString.fromArrayUnsafe(header, 0, 5)
  }

  private def gzipped(bytes: ByteString): ByteString = {
    val baos = new ByteArrayOutputStream()
    val gzos = new GZIPOutputStream(baos)
    try gzos.write(bytes.toArrayUnsafe())
    finally gzos.close()
    ByteString.fromArrayUnsafe(baos.toByteArray)
  }

  /** Highly compressible payload. */
  private def zeros(size: Int): ByteString = ByteString.fromArrayUnsafe(new Array[Byte](size))

  private def bomb: ByteString = gzipped(zeros(BombSize))

  private def streamingError(codec: Codec, bytes: ByteString): StatusException =
    Source
      .single(bytes)
      .via(GrpcProtocolNative.newReader(codec, Limit).frameDecoder)
      .runWith(TestSink[Frame]())
      .request(1)
      .expectError() match {
      case s: StatusException => s
      case other              => fail(s"expected a StatusException, got [$other]")
    }

  private def strictError(codec: Codec, bytes: ByteString): StatusException =
    intercept[StatusException](GrpcProtocolNative.newReader(codec, Limit).decodeSingleFrame(bytes))

  "The streaming frame decoder" should {

    "reject a frame whose declared length exceeds the limit" in {
      // header only: the oversized payload is never sent, so this can only be detected
      // from the declared length, before any of it is buffered
      val status = streamingError(Identity, frameHeader(0, Limit + 1)).getStatus

      status.getCode shouldBe Status.Code.RESOURCE_EXHAUSTED
      status.getDescription should include(s"Frame length ${Limit + 1}")
    }

    "reject a negative frame length with INTERNAL" in {
      streamingError(Identity, frameHeader(0, -1)).getStatus.getCode shouldBe Status.Code.INTERNAL
    }

    "accept a frame exactly at the limit" in {
      val data = zeros(Limit)

      Source
        .single(frameHeader(0, Limit) ++ data)
        .via(GrpcProtocolNative.newReader(Identity, Limit).frameDecoder)
        .runWith(TestSink[Frame]())
        .request(1)
        .expectNext(DataFrame(data))
        .expectComplete()
    }

    "reject a gzip bomb while decompressing rather than after" in {
      val compressed = bomb
      // the frame itself is well within the limit, so the frame length check cannot catch this
      compressed.length should be < Limit

      val status = streamingError(Gzip, frameHeader(1, compressed.length) ++ compressed).getStatus

      status.getCode shouldBe Status.Code.RESOURCE_EXHAUSTED
      // This message is produced only by Gzip's streaming, size-bounded decoder. The generic
      // Codec fallback decompresses in full first and reports the actual size, so asserting
      // on the exact text pins the fail-fast path rather than merely the outcome.
      status.getDescription shouldBe TooLargeWhenDecompressed
    }
  }

  "The strict frame decoder" should {

    "reject a frame whose declared length exceeds the limit" in {
      val status = strictError(Identity, frameHeader(0, Limit + 1) ++ zeros(Limit + 1)).getStatus

      status.getCode shouldBe Status.Code.RESOURCE_EXHAUSTED
      status.getDescription should include(s"Frame length ${Limit + 1}")
    }

    "reject a negative frame length with INTERNAL" in {
      strictError(Identity, frameHeader(0, -1)).getStatus.getCode shouldBe Status.Code.INTERNAL
    }

    "reject a gzip bomb while decompressing rather than after" in {
      val compressed = bomb
      val status = strictError(Gzip, frameHeader(1, compressed.length) ++ compressed).getStatus

      status.getCode shouldBe Status.Code.RESOURCE_EXHAUSTED
      status.getDescription shouldBe TooLargeWhenDecompressed
    }

    "decode a frame within the limit" in {
      val data = zeros(16)

      GrpcProtocolNative.newReader(Identity, Limit).decodeSingleFrame(frameHeader(0, 16) ++ data) shouldBe data
    }
  }

  "Gzip" should {

    "route the compression-bit overload to the size-bounded decoder" in {
      val status = intercept[StatusException](Gzip.uncompress(compressedBitSet = true, bomb, Limit)).getStatus

      status.getCode shouldBe Status.Code.RESOURCE_EXHAUSTED
      status.getDescription shouldBe TooLargeWhenDecompressed
    }

    "decompress payloads within the limit" in {
      val data = zeros(Limit)

      Gzip.uncompress(compressedBitSet = true, gzipped(data), Limit) shouldBe data
    }

    "not reject a payload when the limit is Int.MaxValue" in {
      val data = zeros(64 * 1024)

      Gzip.uncompress(gzipped(data), Int.MaxValue) shouldBe data
    }

    "report a non-positive limit as a gRPC status rather than IllegalArgumentException" in {
      val status = intercept[StatusException](Gzip.uncompress(gzipped(zeros(16)), -1)).getStatus

      status.getCode shouldBe Status.Code.RESOURCE_EXHAUSTED
    }
  }

  "Identity" should {

    "reject a payload larger than the limit" in {
      val status =
        intercept[StatusException](Identity.uncompress(compressedBitSet = false, zeros(Limit + 1), Limit)).getStatus

      status.getCode shouldBe Status.Code.RESOURCE_EXHAUSTED
    }

    "still reject a set compression bit with INTERNAL" in {
      val status = intercept[StatusException](Identity.uncompress(compressedBitSet = true, zeros(1), Limit)).getStatus

      status.getCode shouldBe Status.Code.INTERNAL
    }
  }

  "newReader" should {

    "reuse a cached reader for the default limit" in {
      assert(GrpcProtocolNative.newReader(Identity) eq GrpcProtocolNative.newReader(Identity))
      assert(GrpcProtocolWeb.newReader(Gzip) eq GrpcProtocolWeb.newReader(Gzip))
    }

    "build a fresh reader for a custom limit" in {
      assert(GrpcProtocolNative.newReader(Identity, Limit) ne GrpcProtocolNative.newReader(Identity))
    }
  }
}
