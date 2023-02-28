enablePlugins(BuildInfoPlugin)

val sbtProtocV = "1.0.6"

buildInfoKeys := Seq[BuildInfoKey]("sbtProtocVersion" -> sbtProtocV)

addSbtPlugin("lt.dvim.authors" % "sbt-authors" % "1.3")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.6")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.7.0")
addSbtPlugin("com.thesamet" % "sbt-protoc" % sbtProtocV)
addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.5.1")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "1.2.0")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.0")
addSbtPlugin("net.bzzt" % "sbt-reproducible-builds" % "0.30")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.3")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.10")
addSbtPlugin("org.mdedetrich" % "sbt-apache-sonatype" % "0.1.5")

// allow access to snapshots for pekko-sbt-paradox
resolvers += "Apache Nexus Snapshots".at("https://repository.apache.org/content/repositories/snapshots/")

// We have to deliberately use older versions of sbt-paradox because current Pekko sbt build
// only loads on JDK 1.8 so we need to bring in older versions of parboiled which support JDK 1.8
addSbtPlugin(("org.apache.pekko" % "pekko-sbt-paradox" % "0.0.0+28-a1dda63b-SNAPSHOT").excludeAll(
  "com.lightbend.paradox", "sbt-paradox",
  "com.lightbend.paradox" % "sbt-paradox-apidoc",
  "com.lightbend.paradox" % "sbt-paradox-project-info"))
addSbtPlugin(("com.lightbend.paradox" % "sbt-paradox" % "0.9.2").force())
addSbtPlugin(("com.lightbend.paradox" % "sbt-paradox-apidoc" % "0.10.1").force())
addSbtPlugin(("com.lightbend.paradox" % "sbt-paradox-project-info" % "2.0.0").force())

addSbtPlugin("com.lightbend.sbt" % "sbt-publish-rsync" % "0.2")
addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.5.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.4.1")

// For RawText
libraryDependencies += "org.eclipse.jgit" % "org.eclipse.jgit" % "5.13.1.202206130422-r"

// scripted testing
libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.11"
