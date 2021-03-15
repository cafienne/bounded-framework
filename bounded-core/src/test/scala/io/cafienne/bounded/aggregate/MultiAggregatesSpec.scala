/*
 * Copyright (C) 2016-2021 Cafienne B.V. <https://www.cafienne.io/bounded>
 */

package io.cafienne.bounded.aggregate

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import com.typesafe.config.ConfigFactory
import io.cafienne.bounded.aggregate.Protocol._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpecLike

import scala.concurrent.Future

object Protocol {

  // Shared traits
  trait MultiCommand extends DomainCommand {
    def replyTo: ActorRef[MultiCommandResponse]
  }
  trait MultiCommandResponse
  trait MultiEvent extends DomainEvent

  // CommandsResponses
  case object Confirmed                       extends MultiCommandResponse
  case class StateResponse(state: MultiState) extends MultiCommandResponse

  // Commands
  case class CommandA(aggregateRootId: String, replyTo: ActorRef[MultiCommandResponse])  extends MultiCommand
  case class CommandB(aggregateRootId: String, replyTo: ActorRef[MultiCommandResponse])  extends MultiCommand
  case class GiveState(aggregateRootId: String, replyTo: ActorRef[MultiCommandResponse]) extends MultiCommand

  // Events
  case class EventA(id: String) extends MultiEvent
  case class EventB(id: String) extends MultiEvent

  // State
  final case class MultiState(events: List[String] = Nil)
}

object AggregateRootA {
  val TAG = "ar-A"

  val commandHandler: (MultiState, MultiCommand) => Effect[MultiEvent, MultiState] = { (state, command) =>
    command match {
      case _: CommandA => Effect.persist(List(EventA(command.aggregateRootId))).thenReply(command.replyTo)(_ => Confirmed)
      case _           => throw new Exception("Whut?")
    }
  }

  val eventHandler: (MultiState, MultiEvent) => MultiState = { (state, event) =>
    event match {
      case EventA(_) => state.copy("A" :: state.events)
      case EventB(_) => state.copy("B" :: state.events) // This should never occur?
    }
  }

  def apply(): AggregateRootA = new AggregateRootA()

  def tagger(evt: MultiEvent): Set[String] = Set(TAG, "aggregate")

}

class AggregateRootA {
  import io.cafienne.bounded.aggregate.AggregateRootA._

  def behavior(id: String): Behavior[MultiCommand] = {
    val persistenceId = PersistenceId.ofUniqueId(id)
    Behaviors.setup { context =>
      EventSourcedBehavior[MultiCommand, MultiEvent, MultiState](
        persistenceId = persistenceId,
        emptyState = MultiState(Nil),
        commandHandler = commandHandler,
        eventHandler = eventHandler
      ).withTagger(tagger)
    }
  }
}

object AggregateRootB {
  val TAG = "ar-B"

  val commandHandler: (MultiState, MultiCommand) => Effect[MultiEvent, MultiState] = { (state, command) =>
    command match {
      case _: CommandB  => Effect.persist(List(EventB(command.aggregateRootId))).thenReply(command.replyTo)(_ => Confirmed)
      case g: GiveState => Effect.none.thenReply(g.replyTo)(_ => StateResponse(state))
      case _            => throw new Exception("Whut?")
    }
  }

  val eventHandler: (MultiState, MultiEvent) => MultiState = { (state, event) =>
    event match {
      case EventA(_) => state.copy("A" :: state.events) // This should never occur?
      case EventB(_) => state.copy("B" :: state.events)
      case _         => state
    }
  }

  def apply(): AggregateRootB = new AggregateRootB()

  def tagger(evt: MultiEvent): Set[String] = Set(TAG, "aggregate")

}

class AggregateRootB {
  import io.cafienne.bounded.aggregate.AggregateRootB._

  def behavior(id: String): Behavior[MultiCommand] = {
    val persistenceId = PersistenceId.ofUniqueId(id)
    Behaviors.setup { context =>
      EventSourcedBehavior[MultiCommand, MultiEvent, MultiState](
        persistenceId = persistenceId,
        emptyState = MultiState(Nil),
        commandHandler = commandHandler,
        eventHandler = eventHandler
      ).withTagger(tagger)
    }
  }
}

class MultiAggregatesSpec extends ScalaTestWithActorTestKit(ConfigFactory.parseString(s"""
    akka.persistence.publish-plugin-commands = on
    akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
    akka.persistence.journal.inmem.test-serialization = on
    akka.actor.warn-about-java-serializer-usage = off
    # snapshot store plugin is NOT defined, things should still work
    akka.persistence.snapshot-store.local.dir = "target/snapshots-${classOf[MultiAggregatesSpec].getName}/"
    #PLEASE NOTE THAT CoordinatedShutdown needs to be disabled as below in order to run the test properly
    #SEE https://doc.akka.io/docs/akka/current/coordinated-shutdown.html at the end of the page
    akka.coordinated-shutdown.terminate-actor-system = off
    akka.coordinated-shutdown.run-by-actor-system-terminate = off
    akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
    akka.cluster.run-coordinated-shutdown-when-down = off
    """)) with AsyncFlatSpecLike with ScalaFutures with BeforeAndAfterAll {

  implicit val commandValidator = new ValidateableCommand[MultiCommand] {
    override def validate(cmd: MultiCommand): Future[MultiCommand] =
      Future.successful(cmd)
  }

  implicit val actorSystem: ActorSystem[_] = system
  implicit val ec                          = system.executionContext
  implicit val scheduler                   = system.scheduler

  "ARs A and B" should "separate events from other each other" in {
    val probeA = testKit.createTestProbe[MultiCommandResponse]
    val probeB = testKit.createTestProbe[MultiCommandResponse]

    val aggregateId = "ar-id"

    val arA = testKit.spawn(AggregateRootA().behavior(aggregateId))

    arA ! CommandA(aggregateId, probeA.ref)
    probeA.expectMessage(Confirmed)

    val arB = testKit.spawn(AggregateRootB().behavior(aggregateId))
    arB ! CommandB(aggregateId, probeB.ref)
    probeB.expectMessage(Confirmed)
    arB ! GiveState(aggregateId, probeB.ref)

    // Because this is an instance of AggregateRootB, we expect only B in the state
    probeB.expectMessage(StateResponse(MultiState(List("B"))))

    Future(assert(true)) // No clue why this is needed..
  }
}
