# Configuration

A gRPC client is configured with a @apidoc[GrpcClientSettings] instance. There are a number of ways of creating one and the API
docs are the best reference. An `ActorSystem` is always required as it is used for default configuration and service discovery.

## By Code

The simplest way to create a client is to provide a static host and port.

Scala
:  @@snip [GrpcClientSettingsCompileOnly](/runtime/src/test/scala/docs/org/apache/pekko/grpc/client/GrpcClientSettingsCompileOnly.scala) { #simple }

Java
:  @@snip [GrpcClientSettingsCompileOnly](/runtime/src/test/java/jdocs/org/apache/pekko/grpc/client/GrpcClientSettingsCompileOnly.java) { #simple }

Further settings can be added via the `with` methods

Scala
:  @@snip [GrpcClientSettingsCompileOnly](/runtime/src/test/scala/docs/org/apache/pekko/grpc/client/GrpcClientSettingsCompileOnly.scala) { #simple-programmatic }

Java
:  @@snip [GrpcClientSettingsCompileOnly](/runtime/src/test/java/jdocs/org/apache/pekko/grpc/client/GrpcClientSettingsCompileOnly.java) { #simple-programmatic }

## By Configuration

Instead a client can be defined in configuration. All client configurations need to be under `pekko.grpc.client`

Scala
:  @@snip [GrpcClientSettingsSpec](/runtime/src/test/scala/org/apache/pekko/grpc/GrpcClientSettingsSpec.scala) { #client-config }

Java
:  @@snip [GrpcClientSettingsSpec](/runtime/src/test/scala/org/apache/pekko/grpc/GrpcClientSettingsSpec.scala) { #client-config }

Clients defined in configuration pick up defaults from `reference.conf`:

`reference.conf`
:  @@snip [reference](/runtime/src/main/resources/reference.conf) { #defaults }

## TLS hostname verification

When TLS is enabled the client verifies that the server's certificate matches the hostname it
connected to (RFC 2818), so a certificate that is otherwise valid and trusted is still rejected if
it was issued for a different host. This is controlled by `verify-hostname`, which defaults to
`true`:

```hocon
pekko.grpc.client."*" {
  verify-hostname = true
}
```

or programmatically:

Scala
:   ```scala
    val settings = GrpcClientSettings.connectToServiceAt("localhost", 8080)
      .withVerifyHostname(true)
    ```

Java
:   ```java
    GrpcClientSettings settings = GrpcClientSettings.connectToServiceAt("localhost", 8080, system)
        .withVerifyHostname(true);
    ```

The hostname that is checked is the authority the client connects to — that is
`override-authority` when it is set, otherwise the service name — not the address that service
discovery resolved to. This matches how gRPC treats an overridden authority, and it is what lets a
client reach a server by IP while still verifying the certificate it expects.

@@@ warning

Setting `verify-hostname = false` accepts any trusted certificate regardless of which host it was
issued for, which removes the protection against a man-in-the-middle that holds any certificate
your trust store accepts. It exists for testing against certificates that do not carry a matching
name, and should not be used in production. The client logs a warning on every channel it creates
while it is disabled.

The setting only applies to the `pekko-http` backend. The `netty` backend always verifies the
hostname and offers no switch to turn it off, so setting `verify-hostname = false` there has no
effect and is logged as a warning.

@@@

## Using Pekko Discovery for Endpoint Discovery

The examples above all use a hard coded host and port for the location of the gRPC service which is the default if you do not configure a `service-discovery-mechanism`.
Alternatively @extref[Pekko Discovery](pekko:discovery/index.html) can be used.
This allows a gRPC client to switch between discovering services via DNS, config, Kubernetes and Consul and others by just changing
the configuration (see [Discovery methods in Pekko Management](https://pekko.apache.org/docs/pekko-management/current/discovery/index.html)).

To see how to config a particular service discovery mechanism see the @extref[Pekko Discovery docs](pekko:discovery/index.html).
Once it is configured a service discovery mechanism name can either be passed into settings or put in the client's configuration.

Scala
:  @@snip [GrpcClientSettingsSpec](/runtime/src/test/scala/org/apache/pekko/grpc/GrpcClientSettingsSpec.scala) { #config-service-discovery }

Java
:  @@snip [GrpcClientSettingsSpec](/runtime/src/test/scala/org/apache/pekko/grpc/GrpcClientSettingsSpec.scala) { #config-service-discovery }

The above example configures the client `project.WithConfigServiceDiscovery` to use `config` based service discovery.

Then to create the `GrpcClientSettings`:

Scala
:  @@snip [GrpcClientSettingsSpec](/runtime/src/test/scala/org/apache/pekko/grpc/GrpcClientSettingsSpec.scala) { #sd-settings }

Java
:  @@snip [GrpcClientSettingsCompileOnly](/runtime/src/test/java/jdocs/org/apache/pekko/grpc/client/GrpcClientSettingsCompileOnly.java) { #sd-settings }

Alternatively if a default instance is available (configured by `pekko.discovery.method`) in your system it can be use like this:

Scala
:  @@snip [GrpcClientSettingsCompileOnly](/runtime/src/test/scala/docs/org/apache/pekko/grpc/client/GrpcClientSettingsCompileOnly.scala) { #provide-sd }

Java
:  @@snip [GrpcClientSettingsCompileOnly](/runtime/src/test/java/jdocs/org/apache/pekko/grpc/client/GrpcClientSettingsCompileOnly.java) { #provide-sd }

 
Currently service discovery is only queried on creation of the client. A client can be automatically re-created, and go via service discovery again,
 if a connection can't be established, see the lifecycle section.
 
## Debug logging

To enable fine grained debug running the following logging configuration can be used.

Put this in a file `grpc-debug-logging.properties`:

```
handlers=java.util.logging.ConsoleHandler
io.grpc.netty.level=FINE
java.util.logging.ConsoleHandler.level=FINE
java.util.logging.ConsoleHandler.formatter=java.util.logging.SimpleFormatter
```

Run with `-Djava.util.logging.config.file=/path/to/grpc-debug-logging.properties`.
