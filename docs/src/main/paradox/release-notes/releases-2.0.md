# Release Notes (2.0.x)

## 2.0.0

@@@ warning

Pekko gRPC 2.0.0 is not binary compatible with Pekko gRPC 1.x. It requires Pekko 2.0.x and Pekko HTTP 2.0.x,
which are not binary compatible with their 1.x releases either. Libraries built against Pekko gRPC 1.x need to be
rebuilt and released for Pekko gRPC 2.x.

@@@

Release notes for Apache Pekko gRPC 2.0.0. This is the first release of a new major version and includes the changes
from the 2.0.0-M1 and 2.0.0-M2 milestone releases. See the GitHub milestones for
[2.0.0-M1](https://github.com/apache/pekko-grpc/milestone/8?closed=1),
[2.0.0-M2](https://github.com/apache/pekko-grpc/milestone/11?closed=1) and
[2.0.0-M3](https://github.com/apache/pekko-grpc/milestone/12?closed=1) for a fuller list of changes.

Read the @ref:[migration guide](../migration-guide-1.x-2.x.md) before upgrading. It lists the API, behavior and
configuration changes, including the changed default values.

### Main changes

* Pekko 2.0.0 and Pekko HTTP 2.0.0 are the new minimum versions
* Java 17 is the new minimum Java version
* Scala 2.12 is no longer supported for the runtime library; the sbt plugin is still built for sbt 1.x (Scala 2.12)
* protobuf-java 4.x (up from 3.25.x), together with ScalaPB 1.0.0 ([PR610](https://github.com/apache/pekko-grpc/pull/610), [PR854](https://github.com/apache/pekko-grpc/pull/854))
* Deprecated code removed ([PR502](https://github.com/apache/pekko-grpc/pull/502))
* Java DSL APIs use `pekko.japi.function.Function` instead of the deprecated `pekko.japi.Function` ([PR484](https://github.com/apache/pekko-grpc/pull/484))
* Server reflection uses the `grpc.reflection.v1` protocol instead of `v1alpha` ([PR599](https://github.com/apache/pekko-grpc/pull/599))
* The sbt plugin supports sbt 1 and sbt 2 ([PR710](https://github.com/apache/pekko-grpc/pull/710)) and is published in the non-legacy Maven style ([PR736](https://github.com/apache/pekko-grpc/pull/736))
* The Maven plugin uses `io.github.blackrock` protoc-jar 3.0.0 instead of the unmaintained `com.github.os72` equivalent ([PR709](https://github.com/apache/pekko-grpc/pull/709))
* The pekko-http client backend verifies the server hostname against its TLS certificate by default ([PR820](https://github.com/apache/pekko-grpc/pull/820), [PR830](https://github.com/apache/pekko-grpc/pull/830), [PR857](https://github.com/apache/pekko-grpc/pull/857))
* Inbound messages are limited to 4 MiB by default, on both the client and the server ([PR818](https://github.com/apache/pekko-grpc/pull/818))
* The default gRPC-Web CORS settings no longer allow credentials from any origin ([PR871](https://github.com/apache/pekko-grpc/pull/871))

### Additions

* Rich error model client API ([PR645](https://github.com/apache/pekko-grpc/pull/645))
* Server reflection sends transitive dependencies ([PR643](https://github.com/apache/pekko-grpc/pull/643))
* Set a deadline per call with `setDeadline` on the request builders ([PR922](https://github.com/apache/pekko-grpc/pull/922))
* The server honours `grpc-timeout`, and the pekko-http client sends it ([PR824](https://github.com/apache/pekko-grpc/pull/824), [PR872](https://github.com/apache/pekko-grpc/pull/872))
* Client-side request compression with the `compression` client setting ([PR927](https://github.com/apache/pekko-grpc/pull/927))
* `deflate` message compression ([PR926](https://github.com/apache/pekko-grpc/pull/926))
* `minimum-tls-version` client setting ([PR823](https://github.com/apache/pekko-grpc/pull/823))
* `max-inbound-message-size` client and server settings ([PR818](https://github.com/apache/pekko-grpc/pull/818), [PR877](https://github.com/apache/pekko-grpc/pull/877))
* Generated Scala sources can use Scala 3 syntax with the `scala3_sources` option, and the Gradle plugin has a `scala3Sources` option ([PR597](https://github.com/apache/pekko-grpc/pull/597), [PR770](https://github.com/apache/pekko-grpc/pull/770))

### Java DSL improvements

* Accept `Iterable`s and wildcard generic types in Java API collection parameters ([PR935](https://github.com/apache/pekko-grpc/pull/935))
* Avoid unnecessary map conversions in the Java DSL ([PR520](https://github.com/apache/pekko-grpc/pull/520), [PR522](https://github.com/apache/pekko-grpc/pull/522))

### Bug Fixes

* Fix `Codecs.negotiate()` dropping supported encodings with whitespace ([PR850](https://github.com/apache/pekko-grpc/pull/850))
* Fix trailer frame handling in `GrpcProtocol` ([PR822](https://github.com/apache/pekko-grpc/pull/822))
* Percent-decode `grpc-message` on the pekko-http client ([PR869](https://github.com/apache/pekko-grpc/pull/869))
* Report malformed response headers as a gRPC status ([PR870](https://github.com/apache/pekko-grpc/pull/870))
* Do not return `NotImplementedError` and `UnsupportedOperationException` messages to the caller ([PR874](https://github.com/apache/pekko-grpc/pull/874))
* Replace the resolver's `assert` with an `IllegalArgumentException` ([PR873](https://github.com/apache/pekko-grpc/pull/873))
* Cancel server streams in the pekko-http client backend ([PR921](https://github.com/apache/pekko-grpc/pull/921))
* Avoid an NPE for a `StatusRuntimeException` without trailers ([PR920](https://github.com/apache/pekko-grpc/pull/920))
* Guard against division by zero in `PekkoHttpClientUtils` ([PR819](https://github.com/apache/pekko-grpc/pull/819))
* Clean up Netty SSL context creation ([PR707](https://github.com/apache/pekko-grpc/pull/707))
* sbt plugin: consistently include proto files in built artifacts ([PR746](https://github.com/apache/pekko-grpc/pull/746))
* sbt plugin: avoid duplicate gRPC resource directories ([PR895](https://github.com/apache/pekko-grpc/pull/895))
* Maven plugin: pass all `protoPaths` as `-I` import parameters to protoc ([PR773](https://github.com/apache/pekko-grpc/pull/773))
* Maven plugin: recompute proto paths after module reconfiguration ([PR949](https://github.com/apache/pekko-grpc/pull/949))
* Maven plugin: return a more helpful error message when the temporary directory is mounted `noexec` ([PR682](https://github.com/apache/pekko-grpc/pull/682))
* Gradle plugin: use a relative path for `logfile_enc` to fix build cache relocatability ([PR661](https://github.com/apache/pekko-grpc/pull/661))

### Performance

* Improved and simplified unary server handlers ([PR686](https://github.com/apache/pekko-grpc/pull/686), [PR905](https://github.com/apache/pekko-grpc/pull/905))
* Encode response data frames through `GrpcProtocolWriter` with deferred writes ([PR909](https://github.com/apache/pekko-grpc/pull/909))
* Avoid buffer copies with a non-synchronized `ByteStringOutputStream` ([PR907](https://github.com/apache/pekko-grpc/pull/907))
* Reduce memory usage in `ByteStringUtil` ([PR826](https://github.com/apache/pekko-grpc/pull/826))
* Use `FastFuture` for already completed futures ([PR390](https://github.com/apache/pekko-grpc/pull/390), [PR863](https://github.com/apache/pekko-grpc/pull/863))

### Dependency Changes

Most of the dependency changes are small patch level upgrades. Some exceptions include:

* Pekko 2.0.0
* Pekko HTTP 2.0.0
* grpc-java 1.85.1
* protobuf-java 4.36.2
* ScalaPB 1.0.0-alpha.6
* sbt-protoc 1.1.0-RC2
* protoc-jar 3.0.0 (`io.github.blackrock`)

