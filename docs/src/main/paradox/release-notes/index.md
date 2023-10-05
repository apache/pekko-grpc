# Release Notes

## 1.0.1

A minor dependency change release. The main aim is to avoid relying on older dependency versions, especially when
there are security issues published for those versions.

### Additions

* Release Scala 2.13 and Scala 3 versions of pekko-grpc-codegen. This jar is used as a sbt plugin but it is useful to also support other Scala versions so other build frameworks can use use it ([`#180`](https://github.com/apache/incubator-pekko-grpc/issues/180)).

### Dependency Upgrades

* Use Gradle 7 when building pekko-grpc-gradle-plugin
* upgrade protobuf-gradle-plugin to 0.9.4
* protobuf-java 3.21.12
* io.grpc dependencies upgraded to 1.54.2
* guava 32.1.2
* Scala 3 dependency changed to 3.3.1

## 1.0.0
Apache Pekko gRPC 1.0.0 is based on Akka gRPC 2.1.6. Pekko came about as a result of Lightbend's decision to make future
Akka releases under a [Business Software License](https://www.lightbend.com/blog/why-we-are-changing-the-license-for-akka),
a license that is not compatible with Open Source usage.

Apache Pekko has changed the package names, among other changes. Config names have changed to use `pekko` instead
of `akka` in their names. Users switching from Akka to Pekko should read our [Migration Guide](https://pekko.apache.org/docs/pekko/current/project/migration-guides.html).

Generally, we have tried to make it as easy as possible to switch existing Akka based projects over to using Pekko.

We have gone through the code base and have tried to properly acknowledge all third party source code in the
Apache Pekko code base. If anyone believes that there are any instances of third party source code that is not
properly acknowledged, please get in touch.

### Bug Fixes

We haven't had to fix any significant bugs that were in Akka gRPC 2.1.6.

### Changes

* In the `org.apache.pekko.grpc.gen.BuildInfo` class, there are 2 properties for Protobuf related versions.
    * `googleProtocVersion` is the version of [Protoc](https://grpc.io/docs/protoc-installation/) that is supported (3.20.1)
    * `googleProtobufJavaVersion` is the version of [Protobuf Java](https://protobuf.dev/getting-started/javatutorial/) that is supported (3.20.3)
    * Akka gRPC 2.1.6 equivalent has just one property, `googleProtobufVersion`.
* The Pekko gRPC plugin is deployed in Maven Central unlike the Akka gRPC plugin which was deployed
  in [Gradle Plugin Portal](https://plugins.gradle.org/). This means that in addition to changing
  the artifact from `akka-grpc-gradle-plugin` to `pekko-grpc-gradle-plugin` you also need to add
  `mavenCentral()` to the `pluginManagement`'s `repositories` entry. See
  [Installation docs](https://pekko.apache.org/docs/pekko-grpc/current/buildtools/gradle.html#installation) for more
  info.
* The naming convention of the Pekko gRPC sbt plugin has changed, i.e. whereas
  with Akka the artifact was named `sbt-akka-grpc` with Pekko it's named
  `pekko-grpc-sbt-plugin` so it's consistent with `pekko-grpc-gradle-plugin`/`pekko-grpc-maven-plugin`.

### Additions

* Scala 3 support
    * the minimum required version is Scala 3.3.0

### Dependency Upgrades
We have tried to limit the changes to third party dependencies that are used in Pekko gRPC 1.0.0. These are some exceptions:

* protobuf-java 3.20.3
* scalatest 3.2.15. Pekko users who have existing tests based on Akka Testkit may need to migrate their tests due to the scalatest upgrade. The [scalatest 3.2 release notes](https://www.scalatest.org/release_notes/3.2.0) have a detailed description of the changes needed.
