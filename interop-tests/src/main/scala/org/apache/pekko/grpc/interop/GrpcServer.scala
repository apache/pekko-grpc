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
