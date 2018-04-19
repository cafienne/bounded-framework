/*
 * Copyright (C) 2016-2018 Cafienne B.V. <https://www.cafienne.io/bounded>
 */

package io.cafienne.bounded.test

import akka.actor._
import akka.testkit.TestProbe
import akka.util.Timeout
import io.cafienne.bounded.aggregate._
import io.cafienne.bounded.akka.persistence.eventmaterializers.AbstractEventMaterializer

import scala.concurrent.duration.Duration


class TestableProjection(implicit system: ActorSystem, timeout: Timeout) {

  def startProjection(projector: AbstractEventMaterializer) = {

  }

  def addEvents(evt: Seq[DomainEvent]) = {

  }

}

object TestableProjection {

  def given(evt: Seq[DomainEvent])(implicit system: ActorSystem, timeout: Timeout): Unit = {

    val storeEventsActor = system.actorOf(Props(classOf[CreateEventsInStoreActor], "tmp"), "create-events-actor")

    implicit val duration: Duration = timeout.duration

    val testProbe = TestProbe()
    testProbe watch storeEventsActor

    evt foreach { event =>
      testProbe.send(storeEventsActor, event)
      testProbe.expectMsgAllConformingOf(classOf[DomainEvent])
    }

    storeEventsActor ! PoisonPill
    testProbe.expectTerminated(storeEventsActor)
  }

}
