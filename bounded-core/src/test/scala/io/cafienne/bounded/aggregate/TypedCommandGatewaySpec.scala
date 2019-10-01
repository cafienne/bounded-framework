/*
 * Copyright (C) 2016-2019 Cafienne B.V. <https://www.cafienne.io/bounded>
 */

package io.cafienne.bounded.aggregate

import java.time.ZonedDateTime

import akka.actor.Props
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.{ExpectingReply, PersistenceId}
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import io.cafienne.bounded.UserContext
import io.cafienne.bounded.aggregate.TypedSimpleAggregate.{Response, SimpleAggregateCommand}
import io.cafienne.bounded.aggregate.typed.{TypedAggregateRootCreator, TypedDefaultCommandGateway}
import org.scalatest.{Matchers, WordSpecLike}

import scala.concurrent.Future

object TypedSimpleAggregate {

  //Reply Type
  sealed trait Response
  case object OK                              extends Response
  final case class KO(reason: String)         extends Response
  final case class Items(items: List[String]) extends Response

  // aggregate ID used for routing commands to the right aggregate and identifying them later on.
  case class SimpleAggregateId(id: String) extends AggregateRootId {
    override def idAsString: String = id
  }

  // Command Type
  trait CommandOrQuery[Reply]                extends ExpectingReply[Reply]
  sealed trait SimpleAggregateCommand[Reply] extends DomainCommand with CommandOrQuery[Reply]

  final case class Create(aggregateRootId: SimpleAggregateId, metaData: CommandMetaData)(
    override val replyTo: ActorRef[Response]
  ) extends SimpleAggregateCommand[Response]

  final case class AddItem(aggregateRootId: SimpleAggregateId, metaData: CommandMetaData, item: String)(
    override val replyTo: ActorRef[Response]
  ) extends SimpleAggregateCommand[Response]

  //NOTE that a GET on the aggregate is not according to the CQRS pattern and added here for testing.
  sealed trait SimpleDirectAggregateQuery[Reply] extends CommandOrQuery[Reply]

  final case class GetItems(aggregateId: PersistenceId)(override val replyTo: ActorRef[Response])
      extends SimpleDirectAggregateQuery[Response]

  // Event Type
  sealed trait SimpleAggregateEvent
  final case class Created(aggregateId: PersistenceId)                 extends SimpleAggregateEvent
  final case class ItemAdded(aggregateId: PersistenceId, item: String) extends SimpleAggregateEvent

  // State Type
  final case class SimpleAggregateState(items: List[String] = List.empty[String]) {
    def update(evt: SimpleAggregateEvent): SimpleAggregateState = {
      evt match {
        case evt: Created   => this
        case evt: ItemAdded => this.copy(items = items.::(evt.item))
      }
    }
  }

  // Command handler logic
  val commandHandler
    : (SimpleAggregateState, SimpleAggregateCommand[_]) => ReplyEffect[SimpleAggregateEvent, SimpleAggregateState] = {
    (state, command) =>
      command match {
        case cmd: Create  => createAggregate(cmd)
        case cmd: AddItem => addItem(cmd)
        //case cmd: GetItems => getItems(cmd)
      }
  }

  private def createAggregate(cmd: Create): ReplyEffect[SimpleAggregateEvent, SimpleAggregateState] = {
    Effect.persist(Created(PersistenceId(cmd.aggregateRootId.idAsString))).thenReply(cmd)(_ => OK)
  }

  private def addItem(cmd: AddItem): ReplyEffect[SimpleAggregateEvent, SimpleAggregateState] = {
    Effect.persist(ItemAdded(PersistenceId(cmd.aggregateRootId.idAsString), cmd.item)).thenReply(cmd)(_ => OK)
  }

//  private def getItems(cmd: GetItems): ReplyEffect[SimpleAggregateEvent, SimpleAggregateState] = {
//    Effect.reply(cmd)(_ => OK)
//  }

  // event handler to keep internal aggregate state
  val eventHandler: (SimpleAggregateState, SimpleAggregateEvent) => SimpleAggregateState = { (state, event) =>
    state.update(event)
  }
}

class SimpleAggregateCreator() extends TypedAggregateRootCreator[SimpleAggregateCommand[Response]] {
  import TypedSimpleAggregate._
  override def props(idToCreate: AggregateRootId): Props = ???

  override def behavior(id: AggregateRootId): Behavior[SimpleAggregateCommand[Response]] = {
    val persistenceId = PersistenceId(id.idAsString)
    EventSourcedBehavior.withEnforcedReplies(
      persistenceId,
      SimpleAggregateState(List.empty[String]),
      commandHandler,
      eventHandler
    )
  }
}

class TypedCommandGatewaySpec extends ScalaTestWithActorTestKit(s"""
    akka.persistence.publish-plugin-commands = on
    akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
    akka.persistence.journal.inmem.test-serialization = on
    # snapshot store plugin is NOT defined, things should still work
    akka.persistence.snapshot-store.local.dir = "target/snapshots-${classOf[TypedCommandGatewaySpec].getName}/"
    """) with WordSpecLike with Matchers {

  import akka.actor.typed.scaladsl.adapter._
  import TypedSimpleAggregate._

  implicit val commandValidator = new ValidateableCommand[SimpleAggregateCommand[Response]] {
    override def validate(cmd: SimpleAggregateCommand[Response]): Future[SimpleAggregateCommand[Response]] =
      Future.successful(cmd)
  }

  "Typed Command Gateway" must {
    val aggregateCreator = new SimpleAggregateCreator()
    val aggregateId      = SimpleAggregateId("test1")
    val commandMetaData = new CommandMetaData {
      override def timestamp: ZonedDateTime = ZonedDateTime.now()

      override def userContext: Option[UserContext] = None
    }

    "Send a command and wait for a Typed reply" in {
      val cmdGateway =
        new TypedDefaultCommandGateway[SimpleAggregateCommand[Response]](system.toUntyped, aggregateCreator)
      val probe = testKit.createTestProbe[Response]()

      cmdGateway.sendAndAsk(Create(aggregateId, commandMetaData)(probe.ref))
      probe.expectMessage(OK)
    }

    "Send a command and let it go" in {
      val cmdGateway =
        new TypedDefaultCommandGateway[SimpleAggregateCommand[Response]](system.toUntyped, aggregateCreator)
      val probe = testKit.createTestProbe[Response]()

      cmdGateway.send(Create(aggregateId, commandMetaData)(probe.ref))
      probe.expectMessage(OK)

    }

  }

}
