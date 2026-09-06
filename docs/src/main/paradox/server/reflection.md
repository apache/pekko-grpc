# Server Reflection

Server Reflection is a [gRPC feature](https://github.com/grpc/grpc/blob/master/doc/server-reflection.md)
that allows 'dynamic' clients, such as command-line tools for debugging, to
discover the protocol used by a gRPC server at run time. They can then use
this metadata to implement things like completion and sending arbitrary
commands.

This is achieved by providing a gRPC service that provides endpoints that
can be used to query this information.

## Providing

The generated service handler includes a convenience method to create a Pekko HTTP 
handler with your service together with Server Reflection: 

Scala
:  @@snip [Main.scala](/sbt-plugin/src/sbt-test/gen-scala-server/04-server-reflection/src/main/scala/example/myapp/helloworld/Main.scala) { #server-reflection }

Java
:  @@snip [Main.java](/sbt-plugin/src/sbt-test/gen-java/02-server-reflection/src/main/java/example/myapp/helloworld/Main.java) { #server-reflection }

For more advanced setups you will have to combine your partial handler
with the `ServerReflection` handler explicitly. 

For example, if you need to combine multiple services, or if you want to use an overload of the 
service factory methods. In these cases, the reflection service can be generated via 
`ServerReflection` and manually concatenated as described in the walkthrough
section on @ref[serving multiple services](walkthrough.md#serving-multiple-services) { }:

Scala
:  @@snip [Main.scala](/sbt-plugin/src/sbt-test/gen-scala-server/04-server-reflection/src/main/scala/example/myapp/helloworld/Main.scala) { #server-reflection-manual-concat }

Java
:  @@snip [Main.java](/sbt-plugin/src/sbt-test/gen-java/02-server-reflection/src/main/java/example/myapp/helloworld/Main.java) { #server-reflection-manual-concat }

## Consuming

The Server Reflection endpoint exposed above can be used for example to consume
the service with [grpc_cli](https://github.com/grpc/grpc/blob/master/doc/command_line_tool.md):

```
$ ./bins/opt/grpc_cli call localhost:8080 helloworld.GreeterService.SayHello "name:\"foo\""
connecting to localhost:8080
Received initial metadata from server:
date : Wed, 08 Jan 2022 16:57:56 GMT
server : pekko-http/1.0.0
message: "Hello, foo"

Received trailing metadata from server:
date : Wed, 08 Jan 2020 16:57:56 GMT
Rpc succeeded with OK status
```
