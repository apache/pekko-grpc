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

# Release Notes (2.0.x)

## 2.0.0-M2

Release notes for Apache Pekko gRPC 2.0.0-M2. See [GitHub Milestone for 2.0.0-M2](https://github.com/apache/pekko-grpc/milestone/11?closed=1) for a fuller list of changes.

This is a milestone release and is aimed at testing this new major version
by early adopters. This is experimental. This release should not be used in production.

### Main Changes

* Pekko 2.0.0-M3 is the new minimum Pekko version
* grpc-java 1.81.0
* protobuf 4.35.0 (up from 3.25.x)
* Maven plugin now uses io.github.blackrock protoc-jar 3.0.0 instead of the unmaintained com.github.os72 equivalent
* sbt plugin supports sbt 1 and 2

## 2.0.0-M1

Release notes for Apache Pekko gRPC 2.0.0-M1. See [GitHub Milestone for 2.0.0-M1](https://github.com/apache/pekko-grpc/milestone/8?closed=1) for a fuller list of changes.

This is a milestone release and is aimed at testing this new major version
by early adopters. This is experimental. This release should not be used in production.

### Main Changes

* Pekko 2.0.0-M1 is the new minimum Pekko version
* Java 17 is the new minimum JRE version
* Scala 2.12 support dropped
* A lot of deprecated code removed
* grpc-java 1.78.0
