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

import java.io.{ InputStream, OutputStream }
import java.util.zip.{ DeflaterOutputStream, InflaterInputStream }

/**
 * The `deflate` message encoding of the gRPC compression spec: the zlib format (RFC 1950),
 * not raw DEFLATE (RFC 1951).
 */
object Deflate extends StreamingZipCodec {
  override val name: String = "deflate"

  override protected def compressingStream(out: OutputStream): OutputStream = new DeflaterOutputStream(out)

  override protected def decompressingStream(in: InputStream): InputStream = new InflaterInputStream(in)
}
