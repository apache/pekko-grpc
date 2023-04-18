# Binary compatibility

If possible we recommend using Pekko gRPC directly from your downstream projects,
and share protocols among projects by distributing the .proto service definition rather
than creating libraries that contain generated code. However, in some cases it can be
convenient to use Pekko gRPC in a library project.

Conflicting versions in transitive dependencies can make upgrading a painful exercise.
To make this easier, starting with version 1.0.0 Pekko gRPC provides binary compatibility
for the runtime library within each major version.
This means if you use a library that in turn uses Pekko gRPC, it should be possible to use
that library with any newer version of Pekko gRPC as well (with the exceptions listed below).

This is especially relevant if you depend on one library that depends on Pekko gRPC
version 'A', and another library that depends on Pekko gRPC version 'B': due to
binary compatibility, you can simply choose the latest version of Pekko gRPC and
use both libraries with that.

## Limitations

No binary compatibility is guaranteed between major versions.

### New features

Features introduced in later versions of Pekko gRPC may not work with code generated
with a previous version of Pekko gRPC.

### Deprecations

Binary compatibility can be broken via a deprecation cycle: an API that has been marked deprecated in version `x.y.0`
may disappear in version `x.(y+1).z`.

### Internal and ApiMayChange API's

Internal API's (designated by the `org.apache.pekko.grpc.internal` package or with the `@InternalApi` annotation) and API's that are still marked `@ApiMayChange` are not guaranteed to remain binary compatible.

Libraries that use such methods may not work in applications that depend on a newer version of Pekko gRPC.

## Upstream libraries

We depend on a number of upstream libraries that don't formally maintain
binary compatibility, such as [ScalaPB](https://scalapb.github.io/) (when
generating Scala code) and [grpc-java](https://github.com/grpc/grpc-java/).
When updates to those libraries introduce incompatibilities it will be decided
on a case-by-case basis, based on the expected impact of the change,
whether the update requires a new major Pekko gRPC version.
