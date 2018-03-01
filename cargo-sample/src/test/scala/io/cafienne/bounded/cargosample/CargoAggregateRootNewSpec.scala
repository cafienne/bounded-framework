// Copyright (C) 2018 the original author or authors.
// See the LICENSE file distributed with this work for additional
// information regarding copyright ownership.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package io.cafienne.bounded.cargosample

import java.time.{ZoneOffset, ZonedDateTime}
import java.util.UUID

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.pattern.ask
import akka.testkit.{TestKit, TestProbe}
import akka.util.Timeout
import io.cafienne.bounded.akka.{AggregateRoot, AggregateRootCreator}
import io.cafienne.bounded.cargosample.aggregate.Cargo.CargoAggregateRootState
import io.cafienne.bounded.cargosample.aggregate.Cargo
import io.cafienne.bounded.cargosample.aggregate.CargoDomainProtocol._
import io.cafienne.bounded.commands._
import io.cafienne.bounded.test.CreateEventsInStoreActor
import io.cafienne.bounded.test.commands.{GetState, TestingCommandHandlerExtension}
import org.scalatest._

import scala.concurrent.Future
import scala.concurrent.duration._

import scala.reflect.runtime._
import scala.reflect.runtime.universe._

//TODO remove TypeTag or ClassTag (or both) depending on the solution for actor creation with debugging options.
class TestableAggregateRoot[A <: AggregateRoot](id: AggregateRootId, evt: AggregateRootEvent, aggregateRootCreator: AggregateRootCreator)
                                                         (implicit system: ActorSystem, timeout: Timeout, tt: TypeTag[A], ctag: reflect.ClassTag[A]) {

  //TODO actors and identifiers need something extra to prevent issues when things are parallel tested.
  private val storeEventsActor = system.actorOf(Props(classOf[CreateEventsInStoreActor], id), "create-events-actor")
  private var handledEvents: List[AggregateRootEvent] = List.empty

  implicit val duration: Duration = timeout.duration

  private val testProbe = TestProbe()
  testProbe watch storeEventsActor

  testProbe.send(storeEventsActor, evt)
  testProbe.expectMsgAllConformingOf(classOf[AggregateRootEvent])

  storeEventsActor ! PoisonPill
  testProbe.expectTerminated(storeEventsActor)

  private var aggregateRootActor: Option[ActorRef] = None

  private def createActor(id: AggregateRootId) = {
    handledEvents = List.empty
    //TODO create an actor that extends the TestingCommandHandlerExtension.
    system.actorOf(Props(new Cargo(id) with TestingCommandHandlerExtension), s"test-aggregate-$id")

    //this initiates Cargo but how to extend it to have the helper debug commandHandlers as found in the extension ?
    //system.actorOf(Props(ctag.runtimeClass, id), s"test-aggregate-$id")
  }

  def when(command: AggregateRootCommand): TestableAggregateRoot[A] = {
    aggregateRootActor = aggregateRootActor.fold(Some(createActor(command.id)))(r => Some(r))
    val aggregateRootProbe = TestProbe()
    aggregateRootProbe watch aggregateRootActor.get

    aggregateRootProbe.send(aggregateRootActor.get, command)
    aggregateRootProbe.expectMsgPF(duration) {
      case Right(msgList: List[AggregateRootEvent]) if msgList.isInstanceOf[List[AggregateRootEvent]] =>
        handledEvents = handledEvents ++ msgList
      case other => throw new IllegalStateException("Actor should receive events not " + other)
    }
    this
  }

  def currentState: Future[AggregateRootState] = {
    aggregateRootActor.fold(Future.failed[AggregateRootState](new IllegalStateException("")))(actor => (actor ? GetState).mapTo[AggregateRootState])
  }

  def events: List[AggregateRootEvent] = {
    handledEvents
  }

}

object TestableAggregateRoot {

  def given[A <: AggregateRoot](id: AggregateRootId, evt: AggregateRootEvent, aggregateRootCreator: AggregateRootCreator)
                                      (implicit system: ActorSystem, timeout: Timeout, tt: TypeTag[A], ctag: reflect.ClassTag[A]): TestableAggregateRoot[A] = {
    new TestableAggregateRoot[A](id, evt, aggregateRootCreator)
  }
}

class CargoAggregateRootNewSpec extends AsyncWordSpec with Matchers with BeforeAndAfterAll {

  implicit val timeout = Timeout(10.seconds) //dilated
  implicit val system = ActorSystem("CargoTestSystem", SpecConfig.testConfigAkkaInMem)

  val userId1 = UserId(UUID.fromString("53f53841-0bf3-467f-98e2-578d360ee572"))
  val userContext = Some(new UserContext {
    override def roles: List[String] = List.empty

    override def userId: UserId = userId1
  })
  val metaData = MetaData(ZonedDateTime.now(ZoneOffset.UTC), userContext, None)

  "CargoAggregateRoot" must {

    "Change the route specification for an existing Cargo Delivery Using AggregateRootTestFixture" in {
      val cargoId3 = CargoId(java.util.UUID.fromString("D31E3C57-E63E-4AD5-A00B-E5FA9196E80D"))
      val trackingId = TrackingId(UUID.fromString("53f53841-0bf3-467f-98e2-578d360ee573"))
      val routeSpecification = RouteSpecification(Location("home"), Location("destination"), ZonedDateTime.parse("2018-03-03T10:15:30+01:00[Europe/Amsterdam]"))
      val cargoPlannedEvent = CargoPlanned(metaData, cargoId3, trackingId, routeSpecification)

      val newRouteSpecification = RouteSpecification(Location("home"), Location("newDestination"),
        ZonedDateTime.parse("2018-03-04T10:45:45+01:00[Europe/Amsterdam]"))
      val specifyNewRouteCommand = SpecifyNewRoute(metaData, cargoId3, newRouteSpecification)

      val ar = TestableAggregateRoot.given[Cargo](cargoId3, cargoPlannedEvent, Cargo).when(specifyNewRouteCommand)

      // You see that this only shows the events that are 'published' via when
      ar.events should contain (NewRouteSpecified(metaData, cargoId3, newRouteSpecification))

      val targetState = CargoAggregateRootState(trackingId, newRouteSpecification)
      ar.currentState map { state => assert(state == targetState) }
    }

  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
 }

}
