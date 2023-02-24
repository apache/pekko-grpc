/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.internal

import org.apache.pekko.grpc.ProtobufSerializer

trait WithProtobufSerializer[T] {
  def protobufSerializer: ProtobufSerializer[T]
}
