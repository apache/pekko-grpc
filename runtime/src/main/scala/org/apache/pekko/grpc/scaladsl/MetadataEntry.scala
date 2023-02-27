/*
 * Copyright (C) 2009-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.scaladsl

import org.apache.pekko.annotation.ApiMayChange
import org.apache.pekko.util.ByteString
import org.apache.pekko.grpc.javadsl

/**
 * Represents a entry (value) in a collection of Metadata.
 */
@ApiMayChange
sealed trait MetadataEntry extends javadsl.MetadataEntry

/**
 * Represents a text metadata entry.
 * @param value The entry value.
 */
case class StringEntry(value: String) extends MetadataEntry with javadsl.StringEntry {

  /**
   * Java API: Get the entry value.
   */
  override def getValue(): String = value
}

/**
 * Represents a binary metadata entry.
 * @param value The entry value.
 */
case class BytesEntry(value: ByteString) extends MetadataEntry with javadsl.BytesEntry {

  /**
   * Java API: Get the entry value.
   */
  override def getValue(): ByteString = value
}
