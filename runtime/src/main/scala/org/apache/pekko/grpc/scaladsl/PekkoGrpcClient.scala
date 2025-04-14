/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.scaladsl

import scala.concurrent.Future

import org.apache.pekko
import pekko.Done
import pekko.annotation.DoNotInherit

/** Common trait of all generated Apache Pekko gRPC scala clients. Not for user extension. */
@DoNotInherit
trait PekkoGrpcClient extends org.apache.pekko.grpc.PekkoGrpcClient {

  /**
   * Initiates a shutdown in which preexisting and new calls are cancelled.
   *
   * This method is only valid for clients that use an internal channel. If the client was created
   * with a shared user-provided channel, the channel itself should be closed.
   *
   * @throws org.apache.pekko.grpc.GrpcClientCloseException if client was created with a user-provided [[pekko.grpc.GrpcChannel]].
   */
  def close(): Future[Done]

  /**
   * A Future that completes successfully when shutdown via close()
   * or exceptionally if a connection can not be established or reestablished
   * after maxConnectionAttempts.
   */
  def closed: Future[Done]
}
