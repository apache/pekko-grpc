# Maven

To get started with Pekko gRPC read the @ref[client](../client/index.md) or @ref[server](../server/index.md) introductions.

## Configuring what to generate

The plugin can be configured to generate either Java or Scala classes, and then server and or client for the chosen language.
By default both client and server in Java are generated.

Java
:   ```xml
    <plugin>
        <groupId>org.apache.pekko</groupId>
        <artifactId>pekko-grpc-maven-plugin</artifactId>
        <version>${pekko.grpc.version}</version>
        <configuration>
          <language>Java</language>
          <generateClient>false</generateClient>
          <generateServer>true</generateServer>
        </configuration>
    </plugin>
    ```

Scala
:   ```xml
    <plugin>
        <groupId>org.apache.pekko</groupId>
        <artifactId>pekko-grpc-maven-plugin</artifactId>
        <version>${pekko.grpc.version}</version>
        <configuration>
          <language>Scala</language>
          <generateClient>false</generateClient>
          <generateServer>true</generateServer>
        </configuration>
    </plugin>
    ```

### Generating server "power APIs"

To additionally generate server "power APIs" that have access to request metadata, as described
@ref[here](../server/details.md#accessing-request-metadata), set the `serverPowerApis` tag as true:

`pom.xml`
:   ```xml
    <plugin>
        ...
        <configuration>
          ...
          <generatorSettings>
            <serverPowerApis>true</serverPowerApis>
          </generatorSettings>
        </configuration>
    </plugin>
    ```

## Proto source directory

By default the plugin looks for `.proto`-files under `src/main/protobuf` (and `src/main/proto`). This can be changed with the `protoPaths` setting,
which is a relative path to the project basedir. The below configuration overrides the proto path to be only `src/main/protobuf`:

`pom.xml`
:   ```xml
    <plugin>
        <groupId>org.apache.pekko</groupId>
        <artifactId>pekko-grpc-maven-plugin</artifactId>
        <version>${pekko.grpc.version}</version>
        <configuration>
          <protoPaths>
            <protoPath>src/main/protobuf</protoPath>
          </protoPaths>
        </configuration>
    </plugin>
    ```

## Loading proto files from artifacts

In gRPC it is common to make the version of the protocol you are supporting
explicit by duplicating the proto definitions in your project.

When using @ref[sbt](sbt.md) as a build system, we also support loading your
proto definitions from a dependency classpath. This is not yet supported
for Maven and @ref[Gradle](gradle.md). If you are interested in this feature
it is tracked in issue [#152](https://github.com/akka/akka-grpc/issues/152).

## JDK 8 support

If you want to use TLS-based negotiation on JDK 8, Pekko gRPC requires JDK 8 update 252 or later. JVM support for ALPN has been backported to JDK 8u252 which is now widely available. Support for using the Jetty ALPN agent has been dropped in Pekko HTTP and therefore is not supported by Pekko gRPC.

## Starting your Pekko gRPC server from Maven

You can start your gRPC application as usual with:

```bash
mvn compile exec:exec
```
