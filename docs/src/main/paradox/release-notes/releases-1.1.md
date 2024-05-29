# Release Notes (1.1.x)

## 1.1.0-M1

Release notes for Apache Pekko gRPC 1.1.0-M1. See [GitHub Milestone](https://github.com/apache/pekko-grpc/milestone/3?closed=1) for fuller list of changes.
As with all milestone releases, this release is not recommended for production use - it is designed to allow users to try out the changes in a test environment.

### Additions

* Support ScalaPB scala3_sources option. ([PR222](https://github.com/apache/pekko-grpc/pull/222))

### Changes

* Make sure trailers are present in StatusRuntimeException. ([PR230](https://github.com/apache/pekko-grpc/pull/230))
* Improve performance of Gzip byte handling. ([PR309](https://github.com/apache/pekko-grpc/pull/309))

### Dependency Changes

Most of the dependency changes are small patch level upgrades. Some exceptions include:

* grpc-java 1.64.0
* protobuf-java 3.25.3
* scalapb 0.11.15
* slf4j 2
