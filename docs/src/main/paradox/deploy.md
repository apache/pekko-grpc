<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# Deployment

You can deploy a Pekko gRPC application just like you would any other JVM-based project. For some general pointers on this topic, see @extref[the deployment section of the Pekko documentation](pekko:additional/deploying.html)

Remember that the cleartext HTTP/2 "h2c with prior knowledge" protocol is not compatible with HTTP/1.1, so if your infrastructure uses any proxies they must either understand this protocol or support generic TCP connections.

## Serve gRPC over HTTPS

To deploy your gRPC service over a HTTPS connection you will have to use an @apidoc[HttpsConnectionContext] as described in the @extref[Pekko-HTTP documentation](pekko-http:server-side/server-https-support.html).

## Example: Kubernetes

As an example, [pekko-grpc-sample-kubernetes-scala](https://pekko.apache.org/docs/pekko-samples/current/pekko-sample-grpc-kubernetes-scala/) is a complete project consisting of two applications (a gRPC service and an HTTP service that consumes the gRPC service) that can be deployed together in Kubernetes.
