# gRPC-Web

@@@note

This feature is **experimental**.

The Java/Scala API's to enable this feature may still change in further
versions of Pekko gRPC, and future versions of this feature may not work with
services generated with older versions of Pekko gRPC.

There may be missing features and bugs in the current implementation. If you
encounter any, you are welcome to share a reproducer in our
[issue tracker](https://github.com/apache/pekko-grpc/issues).

@@@

gRPC cannot be used directly from the browser because of limitations on the
amount of control the Javascript code has on the behavior of the browser.

The [gRPC-Web](https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-WEB.md#readme)
protocol was designed to be usable from a browser, but be easily translatable
into native gRPC calls.

The most common deployment model of gRPC-Web is to use a proxy such
as [Envoy](https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_filters/grpc_web_filter)
to translate between the gRPC-Web frontend and a native gRPC back-end.
This is a great choice with Pekko gRPC services.

However, in some situations it may be operationally simpler if your service
supports gRPC-Web directly. This is now possible with Pekko gRPC as well.

To serve a gRPC service with Pekko gRPC, it is recommended to serve the
native gRPC protocol on a different port than gRPC-Web, as the two protocols
will likely require a different security story. You can use
@apidoc[WebHandler.grpcWebHandler](WebHandler$) to serve your
gRPC-Web endpoint with basic CORS infrastructure in place. To use CORS,
you will need to add the pekko-http-cors dependency to your project:

@@dependency[sbt,Maven,Gradle] {
  group="org.apache.pekko"
  artifact="pekko-http-cors_$scala.binary.version$"
  version="$pekko-http.version$"
}

And then serve the handlers with @apidoc[WebHandler.grpcWebHandler](WebHandler$) like this:

Scala
:  @@snip [Main.scala](/plugin-tester-scala/src/main/scala/example/myapp/CombinedServer.scala) { #grpc-web }

Java
:  @@snip [Main.java](/plugin-tester-java/src/main/java/example/myapp/CombinedServer.java) { #grpc-web }

## CORS and credentials

`WebHandler.defaultCorsSettings` allows **any** origin. Allowing credentials as well would let
a browser send cookies and HTTP authentication on a cross-origin call to your service, and let
the calling page read the response. For a service authenticated that way, any web page your
users visit could act as them — a CSRF vector.

So while the origins are unrestricted, the handler drops the credentials permission: it
answers with `Access-Control-Allow-Origin: *` and no `Access-Control-Allow-Credentials`, and
browsers withhold cookies on cross-origin calls.

If your gRPC-Web clients need credentials, restrict the origins that may use them. The
credentials permission is kept for restricted origins:

Scala
:   ```scala
    import org.apache.pekko.http.cors.scaladsl.model.HttpOriginMatcher
    import org.apache.pekko.http.scaladsl.model.headers.HttpOrigin

    implicit val corsSettings: CorsSettings =
      WebHandler.defaultCorsSettings
        .withAllowedOrigins(HttpOriginMatcher(HttpOrigin("https://example.com")))
    ```

Java
:   ```java
    CorsSettings corsSettings = WebHandler.defaultCorsSettings()
        .withAllowedOrigins(HttpOriginMatcher.create(HttpOrigin.parse("https://example.com")));
    ```

@@@ warning

Pekko gRPC 1.x allowed credentials from any origin. To keep that behaviour, set:

```hocon
pekko.grpc.server.grpc-web {
  allow-credentials-from-any-origin = true
}
```

A warning is then logged for each handler created while the origins are unrestricted. Prefer
restricting the origins instead.

@@@

`reference.conf`
:  @@snip [reference](/runtime/src/main/resources/reference.conf) { #grpc-web-cors }
