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

package org.apache.pekko.grpc

import java.io.ByteArrayInputStream

import scala.util.Random

import org.apache.pekko
import pekko.grpc.internal.{ ByteStringUtils, Gzip }
import pekko.util.ByteString
import org.openjdk.jmh.annotations._

// Microbenchmarks for the buffers used by gzip compression and by reading a message from an InputStream.
class GzipBenchmark extends CommonBenchmark {
  @Param(Array("1024", "65536", "1048576"))
  var size: Int = 0

  private var uncompressed: ByteString = ByteString.empty
  private var compressed: ByteString = ByteString.empty
  private var uncompressedArray: Array[Byte] = Array.emptyByteArray

  @Setup
  def setup(): Unit = {
    // Compressible text, closer to a typical message than random bytes
    val random = new Random(42)
    val words = Vector.fill(256)(random.alphanumeric.take(3 + random.nextInt(8)).mkString)
    val builder = new StringBuilder(size + 16)
    while (builder.length < size) builder.append(words(random.nextInt(words.size))).append(' ')
    uncompressedArray = builder.toString.take(size).getBytes("UTF-8")
    uncompressed = ByteString(uncompressedArray)
    compressed = Gzip.compress(uncompressed)
  }

  @Benchmark
  def compress(): ByteString =
    Gzip.compress(uncompressed)

  @Benchmark
  def uncompress(): ByteString =
    Gzip.uncompress(compressed, Int.MaxValue)

  // A plain ByteArrayInputStream is not a KnownLength stream, so messages larger than the 32 KiB read buffer
  // take the buffering path.
  @Benchmark
  def fromInputStream(): ByteString =
    ByteStringUtils.fromInputStream(new ByteArrayInputStream(uncompressedArray))
}
