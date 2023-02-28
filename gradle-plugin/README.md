# Apache Pekko gRPC Gradle Plugin

Notes on how it works:

The plugin uses a gradle protobuf plugin, and then hooks our custom generators in through the Main class in the
codegen module (and additionally the Main of scalapb-protoc-plugin scalapb for when building Scala projects with gradle)
