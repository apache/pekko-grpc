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

package org.apache.pekko.grpc.gen

object ProtocSettings {

  /** Whitelisted options for the built-in Java protoc plugin */
  val protocJava = Seq("single_line_to_proto_string", "ascii_format_to_string", "retain_source_code_info")

  /** Whitelisted options for the ScalaPB protoc plugin */
  val scalapb = Seq(
    "java_conversions",
    "flat_package",
    "single_line_to_proto_string",
    "ascii_format_to_string",
    "no_lenses",
    "retain_source_code_info",
    "grpc")
}
