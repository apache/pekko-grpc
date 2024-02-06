package org.apache.pekko.grpc.gen.scaladsl

private[scaladsl] class ScalaCompatConstants(emitScala3Sources: Boolean = false) {
  // val WildcardType: String = if (emitScala3Sources) "?" else "_"
  val WildcardImport: String = if (emitScala3Sources) "*" else "_"
}
