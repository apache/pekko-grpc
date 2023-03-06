/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc

import org.apache.pekko
import pekko.annotation.ApiMayChange
import io.grpc.Status
import pekko.grpc.internal.JavaMetadataImpl
import pekko.grpc.scaladsl.{ Metadata, MetadataBuilder }
import pekko.grpc.javadsl.{ Metadata => jMetadata }

@ApiMayChange
class Trailers(val status: Status, val metadata: Metadata) {
  def this(status: Status) = {
    this(status, MetadataBuilder.empty)
  }

  def this(status: Status, metadata: jMetadata) = {
    this(status, metadata.asScala)
  }

  /**
   * Java API: Returns the status.
   */
  def getStatus: Status =
    status

  /**
   * Java API: Returns the trailing metadata.
   */
  def getMetadata: jMetadata =
    new JavaMetadataImpl(metadata)
}

object Trailers {
  def apply(status: Status): Trailers = new Trailers(status)
  def apply(status: Status, metadata: Metadata): Trailers = new Trailers(status, metadata)
}
