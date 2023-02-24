lazy val plugins = (project in file(".")).dependsOn(ProjectRef(file("../../"), "sbt-pekko-grpc"))
// Use this instead of above when importing to IDEA, after publishLocal and replacing the version here
//addSbtPlugin("com.lightbend.akka.grpc" % "sbt-pekko-grpc" % "0.1+32-fd597fcb+20180618-1248")
