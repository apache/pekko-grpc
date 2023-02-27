# Apache Pekko gRPC Gradle Plugin

Notes on how it works:

The plugin uses a gradle protobuf plugin, and then hooks our custom generators in through the Main class in the
pekko-grpc-codegen module (and additionally the Main of pekko-grpc-scalapb-protoc-plugin scalapb for when building Scala
projects with gradle)
