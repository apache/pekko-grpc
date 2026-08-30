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

package org.apache.pekko.grpc.internal

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

import io.grpc.{ CallOptions, Deadline }
import org.apache.pekko.grpc.scaladsl.headers.`Timeout`
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * `applyDeadline` only bounds the wait on the client side. Without `grpc-timeout` on the wire
 * the server has no way to know there is a deadline at all.
 */
class ClientTimeoutHeaderSpec extends AnyWordSpec with Matchers with OptionValues {

  "The pekko-http client" should {

    "send grpc-timeout when the call has a deadline" in {
      val options = CallOptions.DEFAULT.withDeadline(Deadline.after(5, TimeUnit.SECONDS))

      val sent = PekkoHttpClientUtils.timeoutHeader(options)

      sent should have size 1
      // some of the budget is spent building the request, so allow for a little drift
      val requested = `Timeout`.findIn(sent.toIndexedSeq).value
      requested should be <= 5.seconds
      requested should be > 4.seconds
    }

    "send no grpc-timeout when the call has no deadline" in {
      PekkoHttpClientUtils.timeoutHeader(CallOptions.DEFAULT) shouldBe empty
    }

    "send no grpc-timeout when the deadline has already expired" in {
      // such a call is failed outright rather than sent, and a non-positive timeout is not
      // representable on the wire
      val expired = CallOptions.DEFAULT.withDeadline(Deadline.after(-1, TimeUnit.SECONDS))

      PekkoHttpClientUtils.timeoutHeader(expired) shouldBe empty
    }
  }
}
