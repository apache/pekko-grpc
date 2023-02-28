addSbtPlugin("org.apache.pekko" % "sbt-pekko-grpc" % sys.props("project.version"))

libraryDependencies ++= Seq("com.thesamet.scalapb" %% "scalapb-validate-codegen" % "0.3.0")
