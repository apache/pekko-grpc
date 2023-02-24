/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.interop

/**
 * Glue code to start a gRPC server (either pekko-grpc or io.grpc) to test against
 */
abstract class GrpcServer[T] {
  @throws[Exception]
  def start(args: Array[String]): T

  def getPort(binding: T): Int

  @throws[Exception]
  def stop(binding: T): Unit
}
