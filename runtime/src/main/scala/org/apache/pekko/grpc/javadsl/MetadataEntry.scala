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

package org.apache.pekko.grpc.javadsl

import org.apache.pekko
import pekko.annotation.{ ApiMayChange, DoNotInherit }
import pekko.util.ByteString

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
