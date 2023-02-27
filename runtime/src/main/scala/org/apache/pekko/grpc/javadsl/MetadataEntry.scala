/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.javadsl

import org.apache.pekko.annotation.{ ApiMayChange, DoNotInherit }
import org.apache.pekko.util.ByteString

/**
 * Represents metadata entry.
 */
@DoNotInherit
@ApiMayChange
trait MetadataEntry

/**
 * Represents a text metadata entry.
 */
@DoNotInherit
trait StringEntry extends MetadataEntry {
  def getValue(): String
}

/**
 * Represents a binary metadata entry.
 */
@DoNotInherit
trait BytesEntry extends MetadataEntry {
  def getValue(): ByteString
}
