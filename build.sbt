/*** Common Settings ***/

organization in ThisBuild := "com.paytmlabs.akka"
scalaVersion in ThisBuild := "2.11.8"
version in ThisBuild := "0.1.0-SNAPSHOT"

publishTo in ThisBuild := {
 val nexus = "https://nexus.paytmlabs.com/content/repositories"
 if(isSnapshot.value) {
   Some("Paytmlabs Snapshots" at s"$nexus/snapshots/")
 }
 else {
  Some("Paytmlabs Releases" at s"$nexus/releases/")
 }
}

lazy val namePrefix = "akka"

/*** Projects ***/

lazy val root = (project in file("."))
  .settings(publishArtifact := false)
  .aggregate(commons,router,sbr,discoveryCore,discoveryElb)

lazy val commons = (project in file("commons"))
                  .settings(name := s"$namePrefix-commons")
                  .settings(libraryDependencies ++= sharedDependencies)

lazy val router = (project in file("router"))
                  .dependsOn(commons)
                  .settings(name := s"$namePrefix-affinity-router")
                  .settings(libraryDependencies ++= sharedDependencies)

lazy val sbr = (project in file("split-brain-resolver"))
                  .dependsOn(commons)
                  .settings(name := s"$namePrefix-split-brain-resolver")
                  .settings(libraryDependencies ++= sharedDependencies)

lazy val discoveryCore = (project in file("discovery/core"))
                  .dependsOn(commons)
                  .settings(name := s"$namePrefix-cluster-discovery-core")
                  .settings(libraryDependencies ++= sharedDependencies)

lazy val discoveryElb = (project in file("discovery/elb"))
                  .dependsOn(commons, discoveryCore)
                  .settings(name := s"$namePrefix-cluster-discovery-elb")
                  .settings(libraryDependencies ++= discoverElbDependencies)

/*** Dependencies ***/

val AKKA_VERSION                    = "2.4.17"
val AWS_SDK_VERSION                 = "1.11.89"
val SCALA_MOCK_VERSION              = "3.2.2"
val SCALATEST_VERSION               = "3.0.0"

val akkaActor              = "com.typesafe.akka"      %% "akka-actor"               % AKKA_VERSION
val akkaRemote             = "com.typesafe.akka"      %% "akka-remote"              % AKKA_VERSION
val akkaCluster            = "com.typesafe.akka"      %% "akka-cluster"             % AKKA_VERSION
val akkaClusterTools       = "com.typesafe.akka"      %% "akka-cluster-tools"       % AKKA_VERSION
val akkaSlf4j              = "com.typesafe.akka"      %% "akka-slf4j"               % AKKA_VERSION
val awsEC2                 = "com.amazonaws"          % "aws-java-sdk-ec2"          % AWS_SDK_VERSION
val awsELB                 = "com.amazonaws"          % "aws-java-sdk-elasticloadbalancing" % AWS_SDK_VERSION

// Test
val akkaTestKit            = "com.typesafe.akka"      %% "akka-testkit"                % AKKA_VERSION       % "test"
val akkaMultinodeTest      = "com.typesafe.akka"      %% "akka-multi-node-testkit"     % AKKA_VERSION
val scalaTest              = "org.scalatest"          %% "scalatest"                   % SCALATEST_VERSION  % "test"
val scalaMock              = "org.scalamock"          %% "scalamock-scalatest-support" % SCALA_MOCK_VERSION % "test"

val sharedDependencies = Seq(akkaActor, akkaSlf4j, akkaRemote, akkaCluster, akkaClusterTools, akkaTestKit, scalaTest, scalaMock)

val discoverElbDependencies = sharedDependencies ++ Seq(awsEC2, awsELB)

