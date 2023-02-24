/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.interop

/**
 * Glue code to start a gRPC client (either pekko-grpc or io.grpc) to test with
 */
abstract class GrpcClient {
  def run(args: Array[String]): Unit
}
