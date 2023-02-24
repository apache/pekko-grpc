/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc

import org.apache.pekko.annotation.ApiMayChange

import com.google.protobuf.Descriptors.FileDescriptor;

@ApiMayChange
trait ServiceDescription {
  def name: String
  def descriptor: FileDescriptor
}
