/*
 * Copyright (C) 2016-2020 Cafienne B.V. <https://www.cafienne.io/bounded>
 */

package io.cafienne.bounded.aggregate

import java.time.ZonedDateTime

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.util.Timeout
import io.cafienne.bounded.{BuildInfo, RuntimeInfo}
import io.cafienne.bounded.aggregate.typed.DefaultTypedCommandGateway
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpecLike
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class TypedCommandGatewaySpec extends ScalaTestWithActorTestKit(s"""
    akka.persistence.publish-plugin-commands = on
    akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
    akka.persistence.journal.inmem.test-serialization = on
    akka.actor.warn-about-java-serializer-usage = off
    # snapshot store plugin is NOT defined, things should still work
    akka.persistence.snapshot-store.local.dir = "target/snapshots-${classOf[TypedCommandGatewaySpec].getName}/"
    #PLEASE NOTE THAT CoordinatedShutdown needs to be disabled as below in order to run the test properly
    #SEE https://doc.akka.io/docs/akka/current/coordinated-shutdown.html at the end of the page
    akka.coordinated-shutdown.terminate-actor-system = off
    akka.coordinated-shutdown.run-by-actor-system-terminate = off
    akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
    akka.cluster.run-coordinated-shutdown-when-down = off
    """) with AsyncFlatSpecLike with BeforeAndAfterAll {
  //NOTE: Be aware that the config of the TestKit contains the name of the Cluster based on the name of this Spec (TypedClusteredSpec).
  //      When you copy this for your own use, change the name of the cluser.seed-nodes.

  import TypedSimpleAggregate._

  // import akka.actor.typed.scaladsl.adapter._

  implicit val commandValidator = new ValidateableCommand[SimpleAggregateCommand] {
    override def validate(cmd: SimpleAggregateCommand): Future[SimpleAggregateCommand] =
      Future.successful(cmd)
  }

  implicit val ec        = system.executionContext
  implicit val scheduler = system.scheduler

  behavior of "Typed Command Gateway"

  implicit val gatewayTimeout = Timeout(10.seconds)

  implicit val buildInfo   = BuildInfo("spec", "1.0")
  implicit val runtimeInfo = RuntimeInfo("current")

  val commandMetaData = AggregateCommandMetaData(ZonedDateTime.now(), None)

  val creator = new SimpleAggregateCreator()

  val typedCommandGateway = new DefaultTypedCommandGateway[SimpleAggregateCommand](system, creator)

  "Command Gateway" should "send Create command" in {
    val commandMetaData = AggregateCommandMetaData(ZonedDateTime.now(), None)

    val aggregateId = "test0"

    val probe = testKit.createTestProbe[Response]()

    Await.result(typedCommandGateway.send(Create(aggregateId, commandMetaData, probe.ref)), 2.seconds) shouldEqual (())

    val probeAnswer = probe.expectMessage(OK)
    assert(probeAnswer.equals(OK))
  }

  "Command Gateway" should "handle second message via actor in the Map" in {
    val commandMetaData = AggregateCommandMetaData(ZonedDateTime.now(), None)

    val aggregateId = "test0"

    val probe = testKit.createTestProbe[Response]()

    Await.result(typedCommandGateway.send(AddItem(aggregateId, commandMetaData, "new item 1", probe.ref)), 2.seconds) shouldEqual (())

    val probeAnswer = probe.expectMessage(OK)
    assert(probeAnswer.equals(OK))

  }

  "Command Gateway" should "recover after stop message and add to the map" in {
    val commandMetaData = AggregateCommandMetaData(ZonedDateTime.now(), None)

    val aggregateId = "test0"

    val probe = testKit.createTestProbe[Response]()

    Await.result(typedCommandGateway.send(Stop(aggregateId, commandMetaData, probe.ref)), 2.seconds) shouldEqual (())

    probe.expectNoMessage()

    Await.result(typedCommandGateway.send(AddItem(aggregateId, commandMetaData, "new item 2", probe.ref)), 2.seconds) shouldEqual (())

    val probeAnswer = probe.expectMessage(OK)
    assert(probeAnswer.equals(OK))

  }

  //  it should "send a command via sharding and wait for a Typed reply" in {
//
//    val aggregateId = "test1"
//
//    val probe     = testKit.createTestProbe[Response]()
//    val entityRef = sharding.entityRefFor(creator.entityTypeKey, aggregateId)
//    val answer    = entityRef.?(ref => Create(aggregateId, commandMetaData, ref))
//
//    answer.map { result =>
//      assert(result.isInstanceOf[OK.type])
//    }
//  }
  protected override def afterAll(): Unit = {
    Await.ready(typedCommandGateway.shutdown(), 5.seconds)
  }

}
