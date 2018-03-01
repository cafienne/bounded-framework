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
package io.cafienne.bounded.test.commands

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{TestActor, TestKit, TestKitBase}
import akka.util.Timeout
import io.cafienne.bounded.akka.AggregateRootCreator
import io.cafienne.bounded.commands._

import org.scalatest._


class TestCommandGateway[A <: AggregateRootCreator](system: ActorSystem, aggregateRootCreator: A)
(implicit timeout: Timeout) extends TestKit(system) with Matchers {

  implicit val actorSystem = this.system

  val aggregateRootInstanceActors = collection.mutable.Map[AggregateRootId, ActorRef]()

  private def getAggregateRoot(c: AggregateRootCommand)(implicit system: ActorSystem): ActorRef = {
    val actorName = s"${aggregateRootCreator.getClass.getSimpleName}-${c.id.idAsString}"
    aggregateRootInstanceActors.getOrElseUpdate(c.id, system.actorOf(aggregateRootCreator.create(c.id), actorName))
  }

  def sendAndVerify[T <: AggregateRootCommand, R](command: T, f: PartialFunction[Any, R]): R = {
    within(timeout.duration) {
      getAggregateRoot(command) ! command
      expectMsgPF()(f)
//      expectMsgPF() {
//        case Right(msg: Seq[AggregateRootEvent]) => fail("found aggregateroot event !" + msg)
//          //(msg.filter(x => x.isInstanceOf[CargoPlanned]).size) should be(1)
//        case other => fail(s"did not receive DriverEnrolled but $other")
//      }
    }
  }

}