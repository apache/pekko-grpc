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

package org.apache.pekko.grpc.scaladsl.headers

import scala.collection.immutable
import scala.concurrent.duration._

import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * The `grpc-timeout` wire format is a positive integer of at most 8 digits followed by a unit
 * character: https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md
 */
class TimeoutSpec extends AnyWordSpec with Matchers {

  "Timeout.parse" should {

    "read each unit the protocol defines" in {
      `Timeout`.parse("3H").get.timeout shouldBe 3.hours
      `Timeout`.parse("5M").get.timeout shouldBe 5.minutes
      `Timeout`.parse("10S").get.timeout shouldBe 10.seconds
      `Timeout`.parse("500m").get.timeout shouldBe 500.millis
      `Timeout`.parse("250u").get.timeout shouldBe 250.micros
      `Timeout`.parse("100n").get.timeout shouldBe 100.nanos
    }

    "read the largest value the wire format allows" in {
      `Timeout`.parse("99999999S").get.timeout shouldBe 99999999.seconds
    }

    "reject a value with more than 8 digits" in {
      `Timeout`.parse("999999999S").isFailure shouldBe true
    }

    "reject an unknown unit" in {
      `Timeout`.parse("10X").isFailure shouldBe true
    }

    "reject a value that is too short to carry a unit" in {
      `Timeout`.parse("S").isFailure shouldBe true
      `Timeout`.parse("").isFailure shouldBe true
    }

    "reject a non-numeric amount" in {
      `Timeout`.parse("abcS").isFailure shouldBe true
    }
  }

  "Timeout.value" should {

    "use nanoseconds while they fit in 8 digits" in {
      `Timeout`(50.millis).value() shouldBe "50000000n"
    }

    "fall back to coarser units as the value grows" in {
      // 1 second is 1e9 nanoseconds, which needs 10 digits
      `Timeout`(1.second).value() shouldBe "1000000u"
      `Timeout`(10.minutes).value() shouldBe "600000m"
      `Timeout`(2.hours).value() shouldBe "7200000m"
      // 30 days is 2_592_000_000 ms (10 digits) but 2_592_000 s (7), so seconds win
      `Timeout`(30.days).value() shouldBe "2592000S"
      // and past that, minutes: 2000 days is 172_800_000 s (9 digits) but 2_880_000 min (7)
      `Timeout`(2000.days).value() shouldBe "2880000M"
    }

    "round-trip every unit boundary" in {
      val examples = Seq(1.nano, 100.nanos, 1.milli, 50.millis, 1.second, 90.seconds, 10.minutes, 2.hours, 30.days)

      examples.foreach { d =>
        val encoded = `Timeout`(d).value()
        withClue(s"$d encoded as $encoded: ") {
          encoded.length - 1 should be <= 8
          // coarser units truncate, so the decoded value must not exceed the original
          val decoded = `Timeout`.parse(encoded).get.timeout
          decoded should be <= d
          decoded.toMillis.toDouble shouldBe d.toMillis.toDouble +- (d.toMillis * 0.001 + 1)
        }
      }
    }
  }

  "Timeout.findIn" should {

    "find a timeout among other headers" in {
      val hs = immutable.Seq(RawHeader("other", "value"), RawHeader("grpc-timeout", "250m"))

      `Timeout`.findIn(hs) shouldBe Some(250.millis)
    }

    "return None when there is no timeout" in {
      `Timeout`.findIn(immutable.Seq(RawHeader("other", "value"))) shouldBe None
    }

    "ignore a malformed value rather than failing the request" in {
      // the timeout is a hint from the peer; refusing to serve the request would be worse
      `Timeout`.findIn(immutable.Seq(RawHeader("grpc-timeout", "not a timeout"))) shouldBe None
    }
  }
}
