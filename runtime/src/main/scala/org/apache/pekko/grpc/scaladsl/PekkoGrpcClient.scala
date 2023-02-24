/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.scaladsl

import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.annotation.DoNotInherit

/** Common trait of all generated Apache Pekko gRPC clients. Not for user extension. */
@DoNotInherit
trait PekkoGrpcClient {

  /**
   * Initiates a shutdown in which preexisting and new calls are cancelled.
   *
   * This method is only valid for clients that use an internal channel. If the client was created
   * with a shared user-provided channel, the channel itself should be closed.
   *
   * @throws org.apache.pekko.grpc.GrpcClientCloseException if client was created with a user-provided [[org.apache.pekko.grpc.GrpcChannel]].
   */
  def close(): Future[Done]

  /**
   * A Future that completes successfully when shutdown via close()
   * or exceptionally if a connection can not be established or reestablished
   * after maxConnectionAttempts.
   */
  def closed: Future[Done]
}
