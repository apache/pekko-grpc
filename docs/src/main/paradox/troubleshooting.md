<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# Troubleshooting

## Client

```
java.lang.IllegalArgumentException: Could not find Jetty NPN/ALPN or Conscrypt as installed JDK providers
```

Perhaps surprisingly, this error may be thrown when connecting to an insecure
gRPC server with a client that is configured with TLS enabled.

## Server

```
java.util.concurrent.ExecutionException: io.grpc.StatusRuntimeException: UNAVAILABLE: Failed ALPN negotiation: Unable to find compatible protocol
```

This may happen when `pekko.http.server.enable-http2` is not enabled in
the configuration.
