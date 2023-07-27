/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc

import org.apache.pekko
import pekko.grpc.internal.ByteStringUtils
import pekko.util.ByteString

import java.io.InputStream

trait ProtobufSerializer[T] {
  def serialize(t: T): ByteString
  def deserialize(bytes: ByteString): T
  def deserialize(stream: InputStream): T = deserialize(ByteStringUtils.fromInputStream(stream))
}
