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
