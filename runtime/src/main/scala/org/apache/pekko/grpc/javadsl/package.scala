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

package org.apache.pekko.grpc

import org.apache.pekko

package object javadsl {

  /**
   * Helper for creating org.apache.pekko.japi.function.Function instances from Scala
   * functions as Scala 2.11 does not know about SAMs.
   */
  def japiFunction[A, B](f: A => B): pekko.japi.function.Function[A, B] =
    new pekko.japi.function.Function[A, B]() {
      override def apply(a: A): B = f(a)
    }

  /**
   * Helper for creating java.util.function.Function instances from Scala
   * functions as Scala 2.11 does not know about SAMs.
   */
  def javaFunction[A, B](f: A => B): java.util.function.Function[A, B] =
    new java.util.function.Function[A, B]() {
      override def apply(a: A): B = f(a)
    }

  /**
   * Helper for creating Scala partial functions from [[pekko.japi.Function]]
   * instances as Scala 2.11 does not know about SAMs.
   */
  def scalaPartialFunction[A, B](f: pekko.japi.Function[A, B]): PartialFunction[A, B] = {
    case a => f(a)
  }

  /**
   * Helper for creating Scala anonymous partial functions from [[pekko.japi.Function]]
   * instances as Scala 2.11 does not know about SAMs.
   */
  def scalaAnonymousPartialFunction[A, B, C](
      f: pekko.japi.Function[A, pekko.japi.Function[B, C]]): A => PartialFunction[B, C] =
    a => scalaPartialFunction(f(a))
}
