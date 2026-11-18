<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements. See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License. You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Release Notes (1.2.x)

## 1.2.0

Release notes for Apache Pekko gRPC 1.2.0. See [GitHub Milestone for 1.2.0](https://github.com/apache/pekko-grpc/milestone/5?closed=1) for a fuller list of changes.

### Changes

* Pre-announce trailers in server streaming calls ([PR456](https://github.com/apache/pekko-grpc/pull/456))
* improve performance of deserializing ByteString ([PR503](https://github.com/apache/pekko-grpc/pull/503))

### Dependency Changes

* grpc-java 1.75.0 which includes upgraded Netty components to fix potential security issues 
* protobuf-java 3.25.8
* scalapb 0.11.20
* Twirl 2.0.9
* sbt-protoc 1.0.8

### Known Issues

* pekko-grpc-sbt-plugin 1.2.0 doesn't work with sbt versions older than 1.9.0 ([#540](https://github.com/apache/pekko-grpc/issues/540)). We hope to fix this in a future release.
