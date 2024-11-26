/*
 * Copyright (C) 2016-2019 Cafienne B.V. <https://www.cafienne.io/bounded>
 */

import sbt._

object Dependencies {

  val pekkoVersion = "1.1.2"
  val pekkoHttpVersion = "1.1.0"
  val pekkoProjectionSnapshotVersion = "1.1.0-M1"
  val scalaTestVersion = "3.2.19"

  val baseDeps = {
    def pekkoModule(name: String, version: String = pekkoVersion) =
      "org.apache.pekko" %% s"pekko-$name" % version
    Seq(
      pekkoModule("slf4j"),
      pekkoModule("actor"),
      pekkoModule("stream"),
      pekkoModule("persistence"),
      pekkoModule("persistence-typed"),
      pekkoModule("persistence-query"),
      pekkoModule("projection-core", pekkoProjectionSnapshotVersion),
      pekkoModule("projection-eventsourced", pekkoProjectionSnapshotVersion),
      pekkoModule("cluster-sharding-typed"),
      pekkoModule("cluster"),
      pekkoModule("coordination"),
      pekkoModule("cluster-tools"),
      pekkoModule("stream-testkit") % Test,
      pekkoModule("testkit") % Test,
      pekkoModule("actor-testkit-typed") % Test,
      pekkoModule("persistence-testkit") % Test,
      pekkoModule("projection-testkit",pekkoProjectionSnapshotVersion) % Test,
      "io.spray"                    %% "spray-json"                             % "1.3.6",
      "com.typesafe.scala-logging"  %% "scala-logging"                          % "3.9.5"
    )
  }

  val log = Seq(
      "ch.qos.logback" % "logback-classic" % "1.5.12",
      "net.logstash.logback" % "logstash-logback-encoder" % "8.0"
    )


  val  test = log ++ Seq(
      "org.scalatest"       %% "scalatest"                   % scalaTestVersion % Test
    )


  val akkaHttpDeps = {
    def akkaHttpModule(name: String, version: String = pekkoHttpVersion) =
      "org.apache.pekko" %% s"pekko-$name" % version

    baseDeps ++ Seq(
      akkaHttpModule("http"),
      akkaHttpModule("http-spray-json"),
      akkaHttpModule("http-testkit") % Test
    ) ++ test
  }

  val testDeps = {
    baseDeps ++ Seq(
      "org.scalatest"          %% "scalatest"                 % scalaTestVersion,
      "org.apache.pekko"      %% "pekko-testkit"              % pekkoVersion,
      "org.apache.pekko"      %% "pekko-actor-testkit-typed"  % pekkoVersion,
      "org.apache.pekko"      %% "pekko-persistence-testkit"  % pekkoVersion,
      "org.apache.pekko"      %% "pekko-http-testkit"         % pekkoHttpVersion
    ) ++ test
  }

  val persistenceLevelDBDeps = {
    baseDeps ++ Seq(
      "org.iq80.leveldb"            % "leveldb"        % "0.12",
      "org.fusesource.leveldbjni"   % "leveldbjni-all" % "1.8"
    )
  }

  val persistanceLmdbDBDeps = {
    baseDeps ++ Seq(
      "org.lmdbjava"                % "lmdbjava"        % "0.9.0"
    )
  }

  val persistenceCassandraDeps = {
    baseDeps ++ Seq(
      "org.apache.pekko" %% "pekko-persistence-cassandra" % "1.1.0-M1"
    )
  }

  val persistenceJdbcDeps = {
    baseDeps ++ Seq(
      "org.apache.pekko"   %% "pekko-persistence-r2dbc" % "1.0.0",
      "org.apache.pekko"   %% "pekko-persistence-jdbc"  % "1.1.0",
      "com.typesafe.slick" %% "slick"                   % "3.5.2",
    )
  }

}
