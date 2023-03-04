/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc
import org.apache.pekko
import pekko.grpc.internal.{ Codecs, Gzip, Identity }
import pekko.grpc.scaladsl.headers
import pekko.http.scaladsl.model.HttpRequest
import io.grpc.Status
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.TryValues

import scala.collection.immutable

class CodecsSpec extends AnyWordSpec with Matchers with TryValues {

  private def accept(encodings: String*): HttpRequest =
    HttpRequest(headers = immutable.Seq(headers.`Message-Accept-Encoding`(encodings.mkString(","))))

  private def enc(encodings: String*): HttpRequest =
    HttpRequest(headers = immutable.Seq(headers.`Message-Encoding`(encodings.mkString(","))))

  "Negotiating message encoding with remote client" should {

    "default to Identity if no encoding provided" in {
      Codecs.negotiate(HttpRequest()) should be(Identity)
    }

    "accept explicit Identity" in {
      Codecs.negotiate(accept(Identity.name)) should be(Identity)
    }

    "accept explicit Gzip" in {
      Codecs.negotiate(accept(Gzip.name)) should be(Gzip)
    }

    "use client preference with multiple known encodings" in {
      Codecs.negotiate(accept(Gzip.name, Identity.name)) should be(Gzip)
      Codecs.negotiate(accept(Identity.name, Gzip.name)) should be(Identity)
    }

    "use first known encoding" in {
      Codecs.negotiate(accept("xxxxx", Gzip.name, Identity.name)) should be(Gzip)
    }

    "use default encoding if unknown encodings specified" in {
      Codecs.negotiate(accept("xxxxx")) should be(Identity)
    }

  }

  "Detecting message encoding from remote" should {

    "default to Identity if not specified" in {
      Codecs.detect(HttpRequest()).success.value should be(Identity)
    }

    "accept explicit Identity" in {
      Codecs.detect(enc(Identity.name)).success.value should be(Identity)
    }

    "accept explicit Gzip" in {
      Codecs.detect(enc(Gzip.name)).success.value should be(Gzip)
    }

    "fail with unknown encoding" in {
      val detected = Codecs.detect(enc("xxxxxxx"))
      detected.failure.exception shouldBe a[GrpcServiceException]
      detected.failure.exception.asInstanceOf[GrpcServiceException].status.getCode should be(
        Status.UNIMPLEMENTED.getCode)
    }
  }

}
