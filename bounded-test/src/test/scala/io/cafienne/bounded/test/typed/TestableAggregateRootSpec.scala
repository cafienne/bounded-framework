/*
 * Copyright (C) 2016-2020 Cafienne B.V. <https://www.cafienne.io/bounded>
 */

package io.cafienne.bounded.test.typed

import java.time.{OffsetDateTime, ZonedDateTime}

import akka.actor.testkit.typed.scaladsl.{LogCapturing, ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import akka.util.Timeout
import io.cafienne.bounded.aggregate.typed.TypedAggregateRootManager
import io.cafienne.bounded.{BuildInfo, RuntimeInfo}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpecLike
import akka.actor.typed.scaladsl.adapter._
import akka.persistence.testkit.PersistenceTestKitPlugin
import akka.persistence.testkit.scaladsl.PersistenceTestKit
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

class TestableAggregateRootSpec
    extends ScalaTestWithActorTestKit(PersistenceTestKitPlugin.config.withFallback(ConfigFactory.defaultApplication()))
    with AsyncFlatSpecLike
    with ScalaFutures
    with BeforeAndAfterAll { //with LogCapturing {

  import TypedSimpleAggregate._
  implicit val classicActorSystem = system.toClassic
  implicit val typedTestKit       = testKit
  val persistenceTestKit          = PersistenceTestKit(system)

  behavior of "Testable Typed Aggregate Root"

  implicit val gatewayTimeout = Timeout(10.seconds)
  implicit val buildInfo      = BuildInfo("spec", "1.0")
  implicit val runtimeInfo    = RuntimeInfo("current")
  implicit val actorSytem     = system

  val creator: TypedAggregateRootManager[SimpleAggregateCommand] = new SimpleAggregateManager()

//  override def beforeEach(): Unit = {
//    persistenceTestKit.clearAll()
//  }

  "TestableAggregateRoot" should "be created with initial events" in {
    val commandMetaData = AggregateCommandMetaData(OffsetDateTime.now(), None)
    val eventMetaData   = TestMetaData.fromCommand(commandMetaData)(buildInfo, runtimeInfo)
    val aggregateId     = "ar0"

    val testProbe = TestProbe[Response]()
    val testAR = TestableAggregateRoot
      .given(
        creator,
        aggregateId,
        Created(aggregateId, eventMetaData),
        ItemAdded(aggregateId, eventMetaData, "ar0 item 1")
      )
      .when(AddItem(aggregateId, commandMetaData, "ar0 item 2", testProbe.ref))

    testProbe.expectMessage(OK)

    testAR.events should be(List(ItemAdded(aggregateId, eventMetaData, "ar0 item 2")))

  }

  protected override def afterAll(): Unit = {
//    Await.ready(typedCommandGateway.shutdown(), 5.seconds)
  }

}
