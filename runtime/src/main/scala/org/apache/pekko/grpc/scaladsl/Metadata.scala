/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.scaladsl

import org.apache.pekko
import pekko.annotation.{ ApiMayChange, DoNotInherit, InternalApi }
import pekko.util.ByteString

/**
 * Immutable representation of the metadata in a call
 *
 * Not for user extension
 */
@ApiMayChange
@DoNotInherit trait Metadata {

  /**
   * INTERNAL API
   */
  @InternalApi
  private[grpc] val raw: Option[io.grpc.Metadata] = None

  /**
   * @return The text header value for `key` if one exists, if the same key has multiple values the last occurrence
   *         that is a text key is used.
   */
  def getText(key: String): Option[String]

  /**
   * @return The binary header value for `key` if one exists, if the same key has multiple values the last occurrence
   *         that is a text key is used.
   */
  def getBinary(key: String): Option[ByteString]

  /**
   * @return The metadata as a map.
   */
  @ApiMayChange
  def asMap: Map[String, List[MetadataEntry]]

  /**
   * @return A list of (key, MetadataEntry) tuples.
   */
  @ApiMayChange
  def asList: List[(String, MetadataEntry)]
}
