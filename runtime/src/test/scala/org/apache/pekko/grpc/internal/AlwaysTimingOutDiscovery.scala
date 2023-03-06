/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.internal

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko
import pekko.discovery.Lookup
import pekko.discovery.ServiceDiscovery
import pekko.discovery.ServiceDiscovery.Resolved
import pekko.pattern.AskTimeoutException

class AlwaysTimingOutDiscovery extends ServiceDiscovery {
  def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] =
    Future.failed(new AskTimeoutException("Simulated timeout"))
}
