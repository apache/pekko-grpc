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
import pekko.grpc.GrpcProtocol
import pekko.grpc.scaladsl.headers
import pekko.http.scaladsl.model.{ ContentTypes, HttpHeader, HttpRequest }
import pekko.util.ByteString
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.immutable

class AdaptiveGzipSpec extends AnyWordSpec with Matchers {

  private val smallData = ByteString.fromArrayUnsafe(new Array[Byte](100))
  private val largeData = ByteString.fromArrayUnsafe(new Array[Byte](4096))

  private def nativeRequest(acceptEncoding: String = null, encoding: String = null): HttpRequest = {
    var hdrs: immutable.Seq[HttpHeader] = immutable.Seq.empty
    if (acceptEncoding != null)
      hdrs = hdrs :+ headers.`Message-Accept-Encoding`(acceptEncoding)
    if (encoding != null)
      hdrs = hdrs :+ headers.`Message-Encoding`(encoding)
    HttpRequest(
      headers = hdrs,
      entity = pekko.http.scaladsl.model.HttpEntity(ContentTypes.`application/grpc+proto`, ByteString.empty))
  }

  "AdaptiveGzip" should {

    "not compress data below threshold" in {
      val codec = AdaptiveGzip(1024)
      val result = codec.compress(smallData)
      result should be theSameInstanceAs smallData
    }

    "compress data above threshold" in {
      val codec = AdaptiveGzip(1024)
      val result = codec.compress(largeData)
      result should not be theSameInstanceAs(largeData)
      result.length should be < largeData.length
    }

    "use default threshold of 1024" in {
      val codec = AdaptiveGzip()
      codec.compressionThreshold should be(1024)
    }

    "report name as gzip" in {
      AdaptiveGzip().name should be("gzip")
    }

    "report isCompressed as true" in {
      AdaptiveGzip().isCompressed should be(true)
    }

    "uncompress data compressed by Gzip" in {
      val codec = AdaptiveGzip(1024)
      val compressed = Gzip.compress(largeData)
      val uncompressed = codec.uncompress(compressed)
      uncompressed should be(largeData)
    }

    "uncompress with compressedBitSet=true" in {
      val codec = AdaptiveGzip(1024)
      val compressed = Gzip.compress(largeData)
      val uncompressed = codec.uncompress(compressedBitSet = true, compressed)
      uncompressed should be(largeData)
    }

    "pass through with compressedBitSet=false" in {
      val codec = AdaptiveGzip(1024)
      val uncompressed = codec.uncompress(compressedBitSet = false, smallData)
      uncompressed should be theSameInstanceAs smallData
    }

    "round-trip: compress then uncompress preserves data" in {
      val codec = AdaptiveGzip(1024)
      val data = ByteString("Hello, World! " * 200)
      val compressed = codec.compress(data)
      val uncompressed = codec.uncompress(compressedBitSet = true, compressed)
      uncompressed should be(data)
    }

    "round-trip: pass-through for small data" in {
      val codec = AdaptiveGzip(1024)
      val data = ByteString("small")
      val result = codec.compress(data)
      result should be theSameInstanceAs data
      codec.uncompress(compressedBitSet = false, result) should be(data)
    }

    "not compress empty data" in {
      val codec = AdaptiveGzip(1024)
      val data = ByteString.empty
      val result = codec.compress(data)
      result should be theSameInstanceAs data
    }

    "always compress when threshold is 0" in {
      val codec = AdaptiveGzip(0)
      val data = ByteString("tiny")
      val result = codec.compress(data)
      result should not be theSameInstanceAs(data)
      codec.uncompress(compressedBitSet = true, result) should be(data)
    }

    "never compress when threshold is Int.MaxValue" in {
      val codec = AdaptiveGzip(Int.MaxValue)
      val result = codec.compress(largeData)
      result should be theSameInstanceAs largeData
    }

    "compress data at exactly the threshold (boundary)" in {
      val codec = AdaptiveGzip(1024)
      val data = ByteString.fromArrayUnsafe(new Array[Byte](1024))
      val result = codec.compress(data)
      result should not be theSameInstanceAs(data)
    }

    "not compress data at threshold minus 1 (boundary)" in {
      val codec = AdaptiveGzip(1024)
      val data = ByteString.fromArrayUnsafe(new Array[Byte](1023))
      val result = codec.compress(data)
      result should be theSameInstanceAs data
    }

    "compressWithFlag returns false for small data" in {
      val codec = AdaptiveGzip(1024)
      val (data, flag) = codec.compressWithFlag(smallData)
      data should be theSameInstanceAs smallData
      flag should be(false)
    }

    "compressWithFlag returns true for large data" in {
      val codec = AdaptiveGzip(1024)
      val (data, flag) = codec.compressWithFlag(largeData)
      data should not be theSameInstanceAs(largeData)
      flag should be(true)
    }

    "cache default instance" in {
      val a = AdaptiveGzip()
      val b = AdaptiveGzip()
      a should be theSameInstanceAs b
    }

    "not cache non-default instances" in {
      val a = AdaptiveGzip(2048)
      val b = AdaptiveGzip(2048)
      a should not be theSameInstanceAs(b)
    }
  }

  "GrpcProtocol.negotiate" should {

    "use AdaptiveGzip when client accepts gzip" in {
      val result = GrpcProtocol.negotiate(nativeRequest(acceptEncoding = "gzip"))
      result shouldBe defined
      val (_, writer) = result.get
      writer.messageEncoding shouldBe an[AdaptiveGzip]
      writer.messageEncoding.name should be("gzip")
    }

    "use AdaptiveGzip when client accepts gzip among multiple encodings" in {
      val result = GrpcProtocol.negotiate(nativeRequest(acceptEncoding = "identity,gzip"))
      result shouldBe defined
      val (_, writer) = result.get
      writer.messageEncoding shouldBe an[AdaptiveGzip]
    }

    "use Identity when client does not accept gzip" in {
      val result = GrpcProtocol.negotiate(nativeRequest(acceptEncoding = "identity"))
      result shouldBe defined
      val (_, writer) = result.get
      writer.messageEncoding should be(Identity)
    }

    "use Identity when no accept-encoding header" in {
      val result = GrpcProtocol.negotiate(nativeRequest())
      result shouldBe defined
      val (_, writer) = result.get
      writer.messageEncoding should be(Identity)
    }

    "use Gzip reader when request has gzip encoding" in {
      val result = GrpcProtocol.negotiate(nativeRequest(acceptEncoding = "gzip", encoding = "gzip"))
      result shouldBe defined
      val (readerTry, writer) = result.get
      val reader = readerTry.get
      reader.messageEncoding should be(Gzip)
      writer.messageEncoding shouldBe an[AdaptiveGzip]
    }

    "use Identity reader when request has no encoding" in {
      val result = GrpcProtocol.negotiate(nativeRequest(acceptEncoding = "gzip"))
      result shouldBe defined
      val (readerTry, _) = result.get
      val reader = readerTry.get
      reader.messageEncoding should be(Identity)
    }

    "use custom compression threshold when specified" in {
      val result = GrpcProtocol.negotiate(nativeRequest(acceptEncoding = "gzip"), 2048)
      result shouldBe defined
      val (_, writer) = result.get
      writer.messageEncoding shouldBe an[AdaptiveGzip]
      writer.messageEncoding.asInstanceOf[AdaptiveGzip].compressionThreshold should be(2048)
    }

    "use default threshold of 1024 when no threshold specified" in {
      val result = GrpcProtocol.negotiate(nativeRequest(acceptEncoding = "gzip"))
      result shouldBe defined
      val (_, writer) = result.get
      writer.messageEncoding.asInstanceOf[AdaptiveGzip].compressionThreshold should be(1024)
    }
  }

  "Streaming frame encoding with AdaptiveGzip" should {

    "set compression flag to 0 for small messages (below threshold)" in {
      val codec = AdaptiveGzip(1024)
      val smallMsg = ByteString.fromArrayUnsafe(new Array[Byte](100))
      val writer = GrpcProtocolNative.newWriter(codec)
      val frame = writer.encodeFrame(GrpcProtocol.DataFrame(smallMsg))
      val chunk = frame.asInstanceOf[pekko.http.scaladsl.model.HttpEntity.Chunk]
      val frameData = chunk.data
      frameData(0) should be(0.toByte)
      frameData.length should be(AbstractGrpcProtocol.FrameHeaderSize + smallMsg.length)
    }

    "set compression flag to 1 for large messages (above threshold)" in {
      val codec = AdaptiveGzip(1024)
      val largeMsg = ByteString.fromArrayUnsafe(new Array[Byte](4096))
      val writer = GrpcProtocolNative.newWriter(codec)
      val frame = writer.encodeFrame(GrpcProtocol.DataFrame(largeMsg))
      val chunk = frame.asInstanceOf[pekko.http.scaladsl.model.HttpEntity.Chunk]
      val frameData = chunk.data
      frameData(0) should be(1.toByte)
    }

    "round-trip small message through encode then decode" in {
      val codec = AdaptiveGzip(1024)
      val writer = GrpcProtocolNative.newWriter(codec)
      val reader = GrpcProtocolNative.newReader(Identity)
      val originalMsg = ByteString("Hello, adaptive compression!")
      val frame = writer.encodeFrame(GrpcProtocol.DataFrame(originalMsg))
      val chunk = frame.asInstanceOf[pekko.http.scaladsl.model.HttpEntity.Chunk]
      val decoded = reader.decodeSingleFrame(chunk.data)
      decoded should be(originalMsg)
    }
  }
}
