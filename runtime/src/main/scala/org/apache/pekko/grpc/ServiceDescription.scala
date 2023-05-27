/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc

import org.apache.pekko.annotation.ApiMayChange

import com.google.protobuf.Descriptors.FileDescriptor

@ApiMayChange
trait ServiceDescription {
  def name: String
  def descriptor: FileDescriptor
}
