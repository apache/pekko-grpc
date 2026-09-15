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

package org.apache.pekko.grpc.javadsl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.pekko.grpc.internal.TelemetrySpi;
import org.apache.pekko.http.javadsl.model.HttpRequest;

/**
 * Instantiated by the telemetry extension through `pekko.grpc.telemetry-class`, so it must be
 * public and have a no-arg constructor.
 */
public class CollectingTelemetrySpi implements TelemetrySpi {

  public static class Request {
    public final String prefix;
    public final String method;
    public final HttpRequest request;

    Request(String prefix, String method, HttpRequest request) {
      this.prefix = prefix;
      this.method = method;
      this.request = request;
    }
  }

  private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());

  public List<Request> requests() {
    return requests;
  }

  @Override
  public <T extends HttpRequest> T onRequest(String prefix, String method, T request) {
    requests.add(new Request(prefix, method, request));
    return request;
  }
}
