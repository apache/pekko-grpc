# Apache Pekko gRPC

Support for building streaming gRPC servers and clients on top
of Apache Pekko Streams.

This library is meant to be used as a building block in projects using the Pekko
toolkit.

## Documentation

- [Pekko gRPC reference](https://pekko.apache.org/docs/pekko-grpc/current/) documentation 

## Project Status

This library is ready to be used in production, but API's and build system plugins are still expected to be improved and [may change](https://pekko.apache.org/docs/pekko/current/common/may-change.html).

The API on both sides (Client and Server) is a simple Pekko Streams-based one.

The client side is
currently implemented on top of [io.grpc:grpc-netty-shaded](https://mvnrepository.com/artifact/io.grpc/grpc-netty-shaded),
we plan to replace this by just [io.grpc:grpc-core](https://mvnrepository.com/artifact/io.grpc/grpc-core) and [Pekko HTTP](https://pekko.apache.org/docs/pekko-http/current/).

As for performance, we are currently relying on the JVM TLS implementation,
which is sufficient for many use cases, but is planned to be replaced with
[conscrypt](https://github.com/google/conscrypt) or [netty-tcnative](https://netty.io/wiki/forked-tomcat-native.html).

## General overview

gRPC is a schema-first RPC framework, where your protocol is declared in a
protobuf definition, and requests and responses will be streamed over an HTTP/2
connection.

Based on a protobuf service definition, pekko-grpc can generate:

* Model classes (using plain protoc for Java or scalapb for Scala)
* The API (as an interface for Java or a trait for Scala), expressed in Pekko Streams `Source`s
* On the server side, code to create a Pekko HTTP route based on your implementation of the API
* On the client side, a client for the API.

## Project structure

The project is split up in a number of subprojects:

* codegen: code generation shared among plugins
* runtime: run-time utilities used by the generated code
* sbt-plugin: the sbt plugin
* scalapb-protoc-plugin: the scalapb Scala model code generation packaged as a protoc plugin, to be used from gradle
* [interop-tests](interop-tests/README.md)

Additionally, 'plugin-tester-java' and 'plugin-tester-scala' contain an example
project in Java and Scala respectively, with both sbt and Gradle configurations.

## Compatibility & support

If used with JDK 8 prior to version 1.8.0_251 you must add an ALPN agent.
See the note in the [Akka HTTP docs](https://doc.akka.io/docs/akka-http/10.1/server-side/http2.html#application-layer-protocol-negotiation-alpn-).

## Building from Source

### Prerequisites
- Make sure you have installed a Java Development Kit (JDK) version 8 or later.
- Make sure you have [sbt](https://www.scala-sbt.org/) installed.
- [Maven](https://www.baeldung.com/install-maven-on-windows-linux-mac) is needed for tasks related to building and testing Maven plugin support.
- [Gradle](https://gradle.org/) is needed for tasks related to building and testing Gradle plugin support. We have `gradlew` scripts that will install the right version of Gradle and run the gradle tasks using it.
- [Graphviz](https://graphviz.gitlab.io/download/) is needed for the scaladoc generation build task, which is part of the release.

### Running the Build
- Open a command window and change directory to your preferred base directory
- Use git to clone the [repo](https://github.com/apache/pekko-grpc) or download a source release from https://pekko.apache.org (and unzip or untar it, as appropriate)
- Change directory to the directory where you installed the source (you should have a file called `build.sbt` in this directory)
- `sbt compile` compiles the main source for project default version of Scala (2.12)
    - `sbt +compile` will compile for all supported versions of Scala
- `sbt test` will compile the code and run the unit tests
- `sbt testQuick` similar to `test` but when repeated in shell mode will only run failing tests
- `sbt package` will build the jars
    - the jars will built into target dirs of the various modules
    - for the the 'runtime' module, the jar will be built to `runtime/target/scala-2.12/`
- `sbt publishLocal` will push the jars to your local Apache Ivy repository
- `sbt publishM2` will push the jars to your local Apache Maven repository
- `sbt docs/paradox` will build the docs (the ones describing the module features)
     - `sbt docs/paradoxBrowse` does the same but will open the docs in your browser when complete
     - the `index.html` file will appear in `target/paradox/site/main/`
- `sbt unidoc` will build the Javadocs for all the modules and load them to one place (may require Graphviz, see Prerequisites above)
     - the `index.html` file will appear in `target/scala-2.13/unidoc/`
- `sbt sourceDistGenerate` will generate source release to `target/dist/`
- The version number that appears in filenames and docs is derived, by default. The derived version contains the most git commit id or the date/time (if the directory is not under git control). 
    - You can set the version number explicitly when running sbt commands
        - eg `sbt "set ThisBuild / version := \"1.0.0\"; sourceDistGenerate"`  
    - Or you can add a file called `version.sbt` to the same directory that has the `build.sbt` containing something like
        - `ThisBuild / version := "1.0.0"`
     
#### Maven plugin
- the Maven plugin is built using sbt
- Build the Maven plugin and not its version number because the tests need the version number
- You can test the Maven plugin by changing directory into the `plugin-tester-java` dir
    - `mvn -Dpekko.grpc.project.version=<version> pekko-grpc:generate compile`
- You can run the equivalent Scala tests by changing directory into the `plugin-tester-scala` dir
    - `mvn -Dpekko.grpc.project.version=<version> pekko-grpc:generate scala:compile`

#### Gradle plugin
- the Gradle plugin is built using gradle
- The gradle plugin will automatically derive the version of the artifact from sbt.
  - In other words sbt is the source of truth when it comes to deriving the version
- You can test the Gradle plugin by change directory into the `plugin-tester-java` dir
  - `./gradlew clean test -Dpekko.grpc.project.version=<version>`
- You can run the equivalent Scala tests by changing directory into the `plugin-tester-scala` dir
  - `./gradlew clean test -Dpekko.grpc.project.version=<version>`

## License

Pekko gRPC is Open Source and available under the Apache 2 License.
