/*
 * Copyright (C) 2016-2021 Cafienne B.V. <https://www.cafienne.io/bounded>
 */

package io.cafienne.bounded.aggregate

import akka.actor.testkit.typed.scaladsl.{LogCapturing, LoggingTestKit, ManualTime, ScalaTestWithActorTestKit}
import akka.actor.typed.ActorSystem
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.cafienne.bounded.aggregate.TypedAnotherAggregate.AnotherAddCommand
import io.cafienne.bounded.aggregate.typed.DefaultTypedCommandGateway
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpecLike

import java.time.OffsetDateTime
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

//ManualTime.config.withFallback(
class TypedMultiCommandGatewaySpec extends ScalaTestWithActorTestKit(ConfigFactory.parseString(s"""
    akka.persistence.publish-plugin-commands = on
    akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
    akka.persistence.journal.inmem.test-serialization = on
    akka.actor.warn-about-java-serializer-usage = off
    # snapshot store plugin is NOT defined, things should still work
    akka.persistence.snapshot-store.local.dir = "target/snapshots-${classOf[TypedMultiCommandGatewaySpec].getName}/"
    #PLEASE NOTE THAT CoordinatedShutdown needs to be disabled as below in order to run the test properly
    #SEE https://doc.akka.io/docs/akka/current/coordinated-shutdown.html at the end of the page
    akka.coordinated-shutdown.terminate-actor-system = off
    akka.coordinated-shutdown.run-by-actor-system-terminate = off
    akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
    akka.cluster.run-coordinated-shutdown-when-down = off
    """)) with AsyncFlatSpecLike with ScalaFutures with BeforeAndAfterAll {

  import TypedSimpleAggregate._

  implicit val commandValidator = new ValidateableCommand[SimpleAggregateCommand] {
    override def validate(cmd: SimpleAggregateCommand): Future[SimpleAggregateCommand] =
      Future.successful(cmd)
  }

  implicit val actorSystem: ActorSystem[_] = system
  implicit val ec                          = system.executionContext
  implicit val scheduler                   = system.scheduler

//  val manualTime: ManualTime = ManualTime()

  behavior of "Multi Typed Command Gateway"

  implicit val gatewayTimeout = Timeout(10.seconds)

  val commandMetaData     = AggregateCommandMetaData(OffsetDateTime.now(), None)
  val creator             = new SimpleAggregateManager()
  val typedCommandGateway = new DefaultTypedCommandGateway[SimpleAggregateCommand](system, creator, 6.seconds)

  val anotherCreator = new AnotherAggregateManager()
  val anotherTypedCommandGateway =
    new DefaultTypedCommandGateway[SimpleAggregateCommand](system, anotherCreator, 6.seconds)

  "Command Gateway" should "be able to be created multiple times to route to other aggregates" in {
    assert(anotherTypedCommandGateway != null)
  }

  "Command Gateway" should "be able to be send commands to multiple aggregates" in {
    val probe       = testKit.createTestProbe[Response]()
    val aggregateId = "ar1"

    assert(anotherTypedCommandGateway != null)
    val otherAggregateId = "other1"
    whenReady(
      anotherTypedCommandGateway.tell(AnotherAddCommand(otherAggregateId, commandMetaData, "other item 3", probe.ref))
    ) { answer =>
      answer shouldEqual (())
      val probeAnswer2 = probe.expectMessage(OK)
      assert(probeAnswer2.equals(OK))
    }

    whenReady(typedCommandGateway.tell(AddItem(aggregateId, commandMetaData, "item 1", probe.ref))) { answer =>
      answer shouldEqual (())
      val probeAnswer2 = probe.expectMessage(OK)
      assert(probeAnswer2.equals(OK))
    }

  }

  protected override def afterAll(): Unit = {
    Await.ready(typedCommandGateway.shutdown(), 5.seconds).map(_ => (): Unit)
  }

}
