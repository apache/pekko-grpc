/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.internal

import java.io.{ InputStream, OutputStream }
import java.util.zip.{ GZIPInputStream, GZIPOutputStream }

object Gzip extends StreamingZipCodec {
  override val name: String = "gzip"

  override protected def compressingStream(out: OutputStream): OutputStream = new GZIPOutputStream(out)

  override protected def decompressingStream(in: InputStream): InputStream = new GZIPInputStream(in)
}
