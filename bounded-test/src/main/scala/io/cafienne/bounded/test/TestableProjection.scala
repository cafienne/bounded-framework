/*
 * Copyright (C) 2016-2018 Cafienne B.V. <https://www.cafienne.io/bounded>
 */

package io.cafienne.bounded.test

import akka.actor._
import akka.persistence.inmemory.extension.{InMemoryJournalStorage, InMemorySnapshotStorage, StorageExtension}
import akka.testkit.TestProbe
import akka.util.Timeout
import io.cafienne.bounded.aggregate._
import io.cafienne.bounded.akka.persistence.eventmaterializers.{AbstractEventMaterializer, EventMaterializers}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration

object TestableProjection {

  def given(evt: Seq[DomainEvent])(implicit system: ActorSystem, timeout: Timeout): TestableProjection = {
    val tp = TestProbe()
    tp.send(StorageExtension(system).journalStorage, InMemoryJournalStorage.ClearJournal)
    tp.expectMsg(akka.actor.Status.Success(""))
    tp.send(StorageExtension(system).snapshotStorage, InMemorySnapshotStorage.ClearSnapshots)
    tp.expectMsg(akka.actor.Status.Success(""))

    val testedProjection = new TestableProjection(system, timeout)
    testedProjection.storeEvents(evt)
    testedProjection
  }

}

class TestableProjection private (system: ActorSystem, timeout: Timeout) {
  private var eventMaterializers: Option[EventMaterializers] = _
  private implicit val  executionContext: ExecutionContext = system.dispatcher
  private implicit val actorSystem: ActorSystem = system

  def startProjection(projector: AbstractEventMaterializer): Future[EventMaterializers.ReplayResult] = {
    eventMaterializers = Some(new EventMaterializers(List(projector)))
    eventMaterializers.get.startUp(true).map(list => list.head)
  }

  def addEvents(evt: Seq[DomainEvent]): Unit = {
    eventMaterializers.fold(throw new IllegalStateException("You start the projection before you add events"))(_ => {
      storeEvents(evt)
    })
  }

  // Blocking way to store events.
  private def storeEvents(evt: Seq[DomainEvent]): Unit = {
    val storeEventsActor = system.actorOf(Props(classOf[CreateEventsInStoreActor], evt.head.id), "create-events-actor")

    implicit val duration: Duration = timeout.duration

    val testProbe = TestProbe()
    testProbe watch storeEventsActor

    evt foreach { event =>
      testProbe.send(storeEventsActor, event)
      testProbe.expectMsgAllConformingOf(classOf[DomainEvent])
    }

    storeEventsActor ! PoisonPill
    val terminated = testProbe.expectTerminated(storeEventsActor)
    assert(terminated.existenceConfirmed)
    //This sleep ensures that the persisted events are propagated towards the projections.
    Thread.sleep(2000)
  }

}