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

package org.apache.pekko.grpc.scaladsl.tools

import java.net.{ InetAddress, InetSocketAddress }

import scala.concurrent.Await
import scala.concurrent.duration._

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MutableServiceDiscoverySpec extends AnyWordSpec with Matchers {
  "MutableServiceDiscovery" should {
    "leave unresolved IP addresses absent rather than wrapping null" in {
      val target = InetSocketAddress.createUnresolved("example.invalid", 8080)
      val discovery = new MutableServiceDiscovery(List(target))
      val address = Await.result(discovery.services, 1.second).addresses.head
      address.host shouldBe "example.invalid"
      address.port shouldBe Some(8080)
      address.address shouldBe None
    }

    "preserve resolved IP addresses" in {
      val ip = InetAddress.getLoopbackAddress
      val discovery = new MutableServiceDiscovery(List(new InetSocketAddress(ip, 8080)))
      val address = Await.result(discovery.services, 1.second).addresses.head
      address.address shouldBe Some(ip)
    }
  }
}
