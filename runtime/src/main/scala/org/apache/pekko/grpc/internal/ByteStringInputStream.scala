/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.internal

import java.io.{ ByteArrayInputStream, InputStream, SequenceInputStream }

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.util.ByteString
import pekko.util.ByteString.ByteStrings
import pekko.util.ccompat.JavaConverters._

/** INTERNAL API */
@InternalApi
private[internal] object ByteStringInputStream {

  def apply(bs: ByteString): InputStream = bs match {
    case bss: ByteStrings =>
      new SequenceInputStream(bss.bytestrings.iterator.map(apply).asJavaEnumeration)
    case _ =>
      new ByteArrayInputStream(bs.toArrayUnsafe())
  }
}
