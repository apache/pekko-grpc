# Details

## Accessing request metadata

By default the generated service interfaces don't provide access to the request metadata, only to the request
body (via the RPC method input parameter). If your methods require access to the request  @apidoc[Metadata], you can configure
Pekko gRPC to generate server "power APIs" that extend the base service interfaces to provide an additional
request metadata parameter to each service method. See the detailed chapters on @ref[sbt](../buildtools/sbt.md), @ref[Gradle](../buildtools/gradle.md)
and @ref[Maven](../buildtools/maven.md) for how to set this build option. Note that this option doesn't effect the
generated client stubs.

@java[Notice: you need to change `GreeterServiceHandlerFactory` to `GreeterServiceHandlerFactoryPowerApiHandlerFactory`.]

@scala[Notice: you need to change `GreeterServiceHandler` to `GreeterServicePowerApiHandler`.]

Here's an example implementation of these server power APIs:

Scala
:  @@snip [PowerGreeterServiceImpl.scala](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/PowerGreeterServiceImpl.scala) { #full-service-impl }

Java
:  @@snip [PowerGreeterServiceImpl.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/PowerGreeterServiceImpl.java) { #full-service-impl }

## Status codes

To signal an error, you can fail the @scala[`Future`]@java[`CompletionStage`] or `Source` you are returning with a @apidoc[GrpcServiceException] containing the status code you want to return.

For an overview of gRPC status codes and their meaning see [statuscodes.md](https://github.com/grpc/grpc/blob/master/doc/statuscodes.md).

For unary responses:

Scala
:    @@snip[GrpcExceptionDefaultHandleSpec](/interop-tests/src/test/scala/org/apache/pekko/grpc/scaladsl/GrpcExceptionDefaultHandleSpec.scala) { #unary }

Java
:   @@snip[ExceptionGreeterServiceImpl](/interop-tests/src/test/java/example/myapp/helloworld/grpc/ExceptionGreeterServiceImpl.java) { #unary }

For streaming responses:

Scala
:    @@snip[GrpcExceptionDefaultHandleSpec](/interop-tests/src/test/scala/org/apache/pekko/grpc/scaladsl/GrpcExceptionDefaultHandleSpec.scala) { #streaming }

Java
:   @@snip[ExceptionGreeterServiceImpl](/interop-tests/src/test/java/example/myapp/helloworld/grpc/ExceptionGreeterServiceImpl.java) { #streaming }

## Rich error model
Beyond status codes you can also use the [Rich error model](https://grpc.io/docs/guides/error/#richer-error-model).  

This example uses an error model taken from [common protobuf](https://github.com/googleapis/googleapis/blob/master/google/rpc/error_details.proto) but every class that is based on `scalapb.GeneratedMessage` can be used. Build and return the error as a `PekkoGrpcException`:

Scala
:    @@snip[RichErrorModelSpec](/interop-tests/src/test/scala/org/apache/pekko/grpc/scaladsl/RichErrorModelSpec.scala) { #native_rich_error_model_unary }

Java
:    @@snip[RichErrorModelTest](/interop-tests/src/test/java/example/myapp/helloworld/grpc/RichErrorNativeImpl.java) { #rich_error_model_unary }

Please look @ref[here](../client/details.md) how to handle this on the client.

## Maximum inbound message size

Inbound gRPC messages are limited to 4 MiB by default, matching the grpc-java default. A frame whose
declared length exceeds the limit is rejected before its payload is read, and a compressed frame that
inflates past the limit is rejected while it is being decompressed, so neither an oversized frame nor a
decompression bomb needs to be buffered in full. In both cases the peer sees a `RESOURCE_EXHAUSTED` status.

The limit is read from `pekko.grpc.server`:

`reference.conf`
:  @@snip [reference](/runtime/src/main/resources/reference.conf) { #server-defaults }

To raise it for every service in the actor system, override the setting in your `application.conf`:

```hocon
pekko.grpc.server {
  max-inbound-message-size = 8388608  # 8 MiB
}
```

To use a different limit for a single service, pass @apidoc[GrpcServerSettings] to the generated
handler's `partial` method:

Scala
:   ```scala
    val settings = GrpcServerSettings(system).withMaxInboundMessageSize(8 * 1024 * 1024)
    val handler = GreeterServiceHandler.partial(
      new GreeterServiceImpl(),
      settings = Some(settings))
    ```

Java
:   ```java
    GrpcServerSettings settings =
        GrpcServerSettings.create(system).withMaxInboundMessageSize(8 * 1024 * 1024);
    Function<HttpRequest, CompletionStage<HttpResponse>> handler =
        GreeterServiceHandlerFactory.partial(
            new GreeterServiceImpl(),
            GreeterService.name,
            SystemMaterializer.get(system).materializer(),
            GrpcExceptionHandler.defaultMapper(),
            settings,
            system);
    ```

@@@ note

The limit did not exist before 2.0.0, so a server that previously accepted messages larger than 4 MiB
will start rejecting them after upgrading. Raise `pekko.grpc.server.max-inbound-message-size` to keep
the old behaviour.

Handlers **generated before 2.0.0** call an entry point that has no access to the actor system's
configuration, so they fall back on the 4 MiB default and `pekko.grpc.server.max-inbound-message-size`
has no effect on them. Regenerate your sources against 2.0.0 to make the setting apply.

@@@

## Deadlines

A client can tell the server how long it is prepared to wait by sending the `grpc-timeout`
request header. Generated handlers honour it: when the deadline passes before the service has
replied, the call is completed with `DEADLINE_EXCEEDED` — the same status the client reports for
the same call.

Without this the server would keep working on a reply nobody is going to read, since the client
stops waiting when its own deadline expires.

Both clients send the header when a call has a deadline:

Scala
:   ```scala
    import io.grpc.{ CallOptions, Deadline }
    import java.util.concurrent.TimeUnit

    val settings = GrpcClientSettings.connectToServiceAt("localhost", 8080)
      .withDeadline(5.seconds)
    ```

@@@ note

The deadline bounds the response, not the work behind it. A service that has already started an
expensive operation keeps running it; only the reply is abandoned. To stop the work itself, have
the service observe cancellation of the `Future` or `Source` it returned.

@@@

A request that carries no `grpc-timeout`, or one whose value is malformed, is served without a
deadline. A malformed value is deliberately ignored rather than rejected: the timeout is a hint
from the peer, and refusing the request outright would be a worse outcome than serving it.
