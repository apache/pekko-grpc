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

package org.apache.pekko.grpc.javadsl

import java.util.{ List, Map, Optional }

import org.apache.pekko
import pekko.annotation.{ ApiMayChange, DoNotInherit }
import pekko.util.ByteString
import pekko.japi.Pair
import pekko.grpc.scaladsl

/**
 * Immutable representation of the metadata in a call
 *
 * Not for user extension
 */
@DoNotInherit
@ApiMayChange
trait Metadata {

  /**
   * @return The text header value for `key` if one exists, if the same key has multiple values the last occurrence
   *         that is a text key is used.
   */
  def getText(key: String): Optional[String]

  /**
   * @return The binary header value for `key` if one exists, if the same key has multiple values the last occurrence
   *         that is a text key is used.
   */
  def getBinary(key: String): Optional[ByteString]

  /**
   * @return A map from keys to a list of metadata entries. Entries with the same key will be ordered based on
   *         when they were added or received.
   */
  def asMap(): Map[String, List[MetadataEntry]]

  /**
   * @return A list of (key, entry) pairs. Pairs with the same key will be ordered based on when they were added
   *         or received.
   */
  def asList(): List[Pair[String, MetadataEntry]]

  /**
   * @return Returns the scaladsl.Metadata interface for this instance.
   */
  def asScala: scaladsl.Metadata
}
