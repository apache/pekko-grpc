/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.grpc

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.grpc.internal.AbstractGrpcProtocol
import pekko.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class GrpcServerSettingsSpec extends TestKit(ActorSystem()) with AnyWordSpecLike with Matchers {

  "GrpcServerSettings" should {

    "default to the same maximum inbound message size as grpc-java" in {
      GrpcServerSettings(system).maxInboundMessageSize shouldBe 4 * 1024 * 1024
      GrpcServerSettings(system).maxInboundMessageSize shouldBe AbstractGrpcProtocol.DefaultMaxInboundMessageSize
    }

    "read the maximum inbound message size from config" in {
      val config = ConfigFactory.parseString("max-inbound-message-size = 8388608")

      GrpcServerSettings.fromConfig(config).maxInboundMessageSize shouldBe 8388608
    }

    "fall back on the default when the key is absent" in {
      GrpcServerSettings
        .fromConfig(ConfigFactory.empty())
        .maxInboundMessageSize shouldBe AbstractGrpcProtocol.DefaultMaxInboundMessageSize
    }

    "support overriding the maximum inbound message size" in {
      GrpcServerSettings(system).withMaxInboundMessageSize(1234).maxInboundMessageSize shouldBe 1234
    }

    "reject a non-positive maximum inbound message size" in {
      intercept[IllegalArgumentException](GrpcServerSettings(system).withMaxInboundMessageSize(0))
      intercept[IllegalArgumentException](GrpcServerSettings(system).withMaxInboundMessageSize(-1))
      intercept[IllegalArgumentException](
        GrpcServerSettings.fromConfig(ConfigFactory.parseString("max-inbound-message-size = 0")))
    }

    "expose the same value via the Java API" in {
      GrpcServerSettings.create(system).maxInboundMessageSize shouldBe GrpcServerSettings(system).maxInboundMessageSize
    }
  }
}
