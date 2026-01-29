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

package org.apache.pekko.grpc.scaladsl

import org.apache.pekko
import org.scalatest.Inside.inside
import org.scalatest.Inspectors
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pekko.grpc.GrpcProtocol
import pekko.grpc.internal.{ GrpcProtocolNative, GrpcProtocolWeb, GrpcProtocolWebText }
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.server.{ Directives, Route, UnsupportedRequestContentTypeRejection }
import pekko.http.scaladsl.testkit.ScalatestRouteTest

class GrpcDirectivesSpec extends AnyWordSpec with Matchers with Inspectors with Directives with ScalatestRouteTest {
  import pekko.grpc.scaladsl.GrpcDirectives._

  private val actual = "actual"
  private val exampleStatus = StatusCodes.Created

  private val requestContent = Array[Byte]()

  private def protocolRequests(grpcProtocol: GrpcProtocol*): Seq[HttpRequest] =
    grpcProtocol.flatMap(_.mediaTypes).map { mt =>
      Post("/service",
        HttpEntity(
          ContentType(mt.asInstanceOf[MediaType.Binary]),
          requestContent
        ))
    }

  private def validRequest(route: Route)(request: HttpRequest): Unit = {
    request ~> route ~> check {
      responseAs[String] shouldBe actual
      response.status shouldBe exampleStatus
    }
  }

  private def invalidRequest(route: Route, acceptedProtocols: GrpcProtocol*)(request: HttpRequest): Unit = {
    val expectedContentTypes = acceptedProtocols.flatMap(_.mediaTypes).map(_.asInstanceOf[MediaType.Binary]).map(mt =>
      ContentTypeRange(mt)).toSet
    request ~> route ~> check {
      inside(rejections) {
        case UnsupportedRequestContentTypeRejection(contentTypeRanges) +: _ => contentTypeRanges shouldBe
          expectedContentTypes
      }
    }
  }

  private val nonGrpcRequests = Seq(
    Get("/healthz"),
    Post("/service",
      HttpEntity(
        ContentType(MediaType.applicationBinary("grpc-not", MediaType.Compressible)),
        requestContent
      )),
    Post("/service",
      HttpEntity(
        ContentType(MediaTypes.`application/json`),
        requestContent
      ))
  )

  "The grpc directive" should {
    val route = grpc {
      complete(HttpResponse(exampleStatus, Nil, HttpEntity(actual)))
    }
    "pass only grpc native protocol" in {
      forAll(protocolRequests(GrpcProtocolNative))(validRequest(route))
    }

    "not pass non-grpc native protocols" in {
      forAll(nonGrpcRequests ++ protocolRequests(GrpcProtocolWeb, GrpcProtocolWebText))(invalidRequest(route,
        GrpcProtocolNative))
    }
  }

  "The grpcWeb directive" should {
    val route = grpcWeb {
      complete(HttpResponse(exampleStatus, Nil, HttpEntity(actual)))
    }
    "pass all matching grpc-web protocols" in {
      forAll(protocolRequests(GrpcProtocolWeb, GrpcProtocolWebText))(validRequest(route))
    }

    "not pass non-grpc and non-matching grpc protocols" in {
      forAll(nonGrpcRequests ++ protocolRequests(GrpcProtocolNative))(invalidRequest(route, GrpcProtocolWeb,
        GrpcProtocolWebText))
    }
  }

  "The grpcAll directive" should {
    val route = grpcAll {
      complete(HttpResponse(exampleStatus, Nil, HttpEntity(actual)))
    }
    "pass all grpc protocols" in {
      forAll(protocolRequests(GrpcProtocolNative, GrpcProtocolWeb, GrpcProtocolWebText))(validRequest(route))
    }

    "not pass non-grpc and non-matching grpc protocols" in {
      forAll(nonGrpcRequests)(invalidRequest(route, GrpcProtocolNative, GrpcProtocolWeb, GrpcProtocolWebText))
    }
  }

  "Combined grpc | grpcWeb directive" should {
    val route = (grpc | grpcWeb) {
      complete(HttpResponse(exampleStatus, Nil, HttpEntity(actual)))
    }
    "pass all grpc protocols" in {
      forAll(protocolRequests(GrpcProtocolNative, GrpcProtocolWeb, GrpcProtocolWebText))(validRequest(route))
    }

    "not pass non-grpc and non-matching grpc protocols" in {
      forAll(nonGrpcRequests)(invalidRequest(route, GrpcProtocolNative))
    }
  }

}
