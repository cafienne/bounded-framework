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
package io.cafienne.bounded.test

import akka.actor._
import akka.pattern.ask
import akka.testkit.TestProbe
import akka.util.Timeout
import io.cafienne.bounded.aggregate.AggregateRoot.GetState
import io.cafienne.bounded.aggregate._

import scala.concurrent.Future
import scala.concurrent.duration.Duration

//TODO remove TypeTag or ClassTag (or both) depending on the solution for actor creation with debugging options.
class TestableAggregateRoot[A <: AggregateRoot](id: AggregateRootId, evt: AggregateRootEvent, aggregateRootCreator: AggregateRootCreator)
                                               (implicit system: ActorSystem, timeout: Timeout, ctag: reflect.ClassTag[A]) {

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

  private def createActor[B <: AggregateRoot](id: AggregateRootId) = {
    handledEvents = List.empty
    system.actorOf(Props(ctag.runtimeClass, id), s"test-aggregate-$id")
  }

  def when(command: AggregateRootCommand): TestableAggregateRoot[A] = {
    aggregateRootActor = aggregateRootActor.fold(Some(createActor[A](command.id)))(r => Some(r))
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
                               (implicit system: ActorSystem, timeout: Timeout, ctag: reflect.ClassTag[A]): TestableAggregateRoot[A] = {
    new TestableAggregateRoot[A](id, evt, aggregateRootCreator)
  }
}

