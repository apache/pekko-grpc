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

package org.apache.pekko.grpc.maven

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ProtocSpec extends AnyWordSpec with Matchers {
  "The protoc error messages" must {
    "be parsed into details" in {
      AbstractGenerateMojo.parseError(
        "notifications.proto:12:1: Expected top-level statement (e.g. \"message\").") should
      ===(
        Left(AbstractGenerateMojo
          .ProtocError("notifications.proto", 12, 1, "Expected top-level statement (e.g. \"message\").")))
    }
    "be kept if not parseable" in {
      AbstractGenerateMojo.parseError("My hovercraft is full of eels") should ===(
        Right("My hovercraft is full of eels"))
    }
  }

  import scala.jdk.CollectionConverters._

  "Parsing generator settings" should {
    "filter out the false values" in {
      val settings = Map("1" -> "true", "2" -> "false", "3" -> "False", "4" -> "")
      AbstractGenerateMojo.parseGeneratorSettings(settings.asJava) shouldBe Seq("1", "4")
    }

    "convert camelCase into snake_case of keys" in {
      val settings = Map("flatPackage" -> "true", "serverPowerApis" -> "true")
      AbstractGenerateMojo.parseGeneratorSettings(settings.asJava) shouldBe Seq("flat_package", "server_power_apis")
    }
  }
}
