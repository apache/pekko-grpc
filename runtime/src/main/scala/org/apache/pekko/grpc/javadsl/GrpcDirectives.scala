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

package org.apache.pekko.grpc.javadsl

import org.apache.pekko
import pekko.http.javadsl.server.Route

import java.util.function.Supplier

object GrpcDirectives {

  import pekko.grpc.scaladsl.{ GrpcDirectives => G }
  import pekko.http.javadsl.server.directives.RouteAdapter

  def grpc(inner: Supplier[Route]): Route =
    RouteAdapter {
      G.grpc {
        inner.get() match {
          case ra: RouteAdapter => ra.delegate
        }
      }
    }

  def grpcWeb(inner: Supplier[Route]): Route =
    RouteAdapter {
      G.grpcWeb {
        inner.get() match {
          case ra: RouteAdapter => ra.delegate
        }
      }
    }

  def grpcAll(inner: Supplier[Route]): Route =
    RouteAdapter {
      G.grpcAll {
        inner.get() match {
          case ra: RouteAdapter => ra.delegate
        }
      }
    }

}
