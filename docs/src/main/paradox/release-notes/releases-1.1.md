# Release Notes (1.1.x)

## 1.1.1

Release notes for Apache Pekko gRPC 1.1.1. See [GitHub Milestone for 1.1.1](https://github.com/apache/pekko-grpc/milestone/6?closed=1) for a fuller list of changes.

### Changes

* Merge gRPC headers and trailers in case of failure ([PR391](https://github.com/apache/pekko-grpc/pull/391))

### Dependency Changes

* grpc-java 1.67.1 (this is a downgrade - see [PR399](https://github.com/apache/pekko-grpc/pull/399))

## 1.1.0

Release notes for Apache Pekko gRPC 1.1.0. See [GitHub Milestone for 1.1.0-M1](https://github.com/apache/pekko-grpc/milestone/3?closed=1) and [GitHub Milestone for 1.1.0](https://github.com/apache/pekko-grpc/milestone/4?closed=1) for a fuller list of changes.

### Bug Fixes

* fix: codegen: java: nested protobuf message types ([PR331](https://github.com/apache/pekko-grpc/pull/331)) (not in 1.1.0-M1)

### Additions

* Support ScalaPB scala3_sources option. ([PR222](https://github.com/apache/pekko-grpc/pull/222))
* Publish `pekko-grpc-scalapb-protoc-plugin` for Scala 2.13 and Scala 3 ([PR327](https://github.com/apache/pekko-grpc/pull/327)) (not in 1.1.0-M1)

### Changes

* Make sure trailers are present in StatusRuntimeException. ([PR230](https://github.com/apache/pekko-grpc/pull/230))
* Improve performance of Gzip byte handling. ([PR309](https://github.com/apache/pekko-grpc/pull/309))

### Dependency Changes

Most of the dependency changes are small patch level upgrades. Some exceptions include:

* grpc-java 1.68.0 (this grpc-java release has since been marked as a mistake)
* protobuf-java 3.25.5
* scalapb 0.11.17
* Twirl 2.0.7
* slf4j 2.0.16
