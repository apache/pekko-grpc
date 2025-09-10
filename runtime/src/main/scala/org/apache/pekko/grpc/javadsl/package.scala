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

package org.apache.pekko.grpc

import scala.annotation.nowarn

import org.apache.pekko

package object javadsl {

  /**
   * Helper for creating Scala partial functions from [[pekko.japi.function.Function]]
   * instances.
   */
  @deprecated("no longer needed since support for Scala 2.11 has been dropped", "1.2.0")
  def scalaPartialFunction[A, B](f: pekko.japi.function.Function[A, B]): PartialFunction[A, B] = {
    case a => f(a)
  }

  /**
   * Helper for creating Scala anonymous partial functions from [[pekko.japi.function.Function]]
   * instances.
   */
  @nowarn("msg=deprecated")
  def scalaAnonymousPartialFunction[A, B, C](
      f: pekko.japi.function.Function[A, pekko.japi.function.Function[B, C]]): A => PartialFunction[B, C] =
    a => scalaPartialFunction(f(a))
}
