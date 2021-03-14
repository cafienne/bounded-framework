/*
 * Copyright (C) 2016-2021 Cafienne B.V. <https://www.cafienne.io/bounded>
 */

package io.cafienne.bounded.aggregate

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.typesafe.scalalogging.Logger
import io.cafienne.bounded.aggregate.typed.TypedAggregateRootManager

import scala.concurrent.duration._
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.persistence.RecoveryCompleted
import io.cafienne.bounded.SampleProtocol.{CommandMetaData, MetaData}
import io.cafienne.bounded.aggregate.TypedSimpleAggregate.SimpleAggregateCommand

//This another aggregate is for testing the use of command gateways with multiple aggregates.
object TypedAnotherAggregate {
  val aggregateRootTag = "ar-another"
  import TypedSimpleAggregate._

  final case class AnotherAddCommand(
    aggregateRootId: String,
    metaData: CommandMetaData,
    item: String,
    replyTo: ActorRef[Response]
  ) extends SimpleAggregateCommand

  final case class AnotherAdded(id: String, metaData: MetaData, item: String) extends SimpleAggregateEvent

  final case class AnotherAggregateState(items: List[String] = List.empty[String]) {
    def update(evt: SimpleAggregateEvent): AnotherAggregateState = {
      evt match {
        case evt: Created      => this
        case evt: ItemAdded    => this.copy(items = items.::(evt.item))
        case evt: StoppedAfter => this
        case evt: AnotherAdded => this.copy(items = items.::(evt.item))
      }
    }
  }

  var replayed = false

  final val logger = Logger(TypedSimpleAggregate.getClass.getSimpleName)

  // Command handler logic
  def commandHandler(
    timers: TimerScheduler[SimpleAggregateCommand]
  ): (AnotherAggregateState, SimpleAggregateCommand) => ReplyEffect[SimpleAggregateEvent, AnotherAggregateState] = {
    (state, command) =>
      logger.debug("Received command: " + command)
      command match {
        case cmd: Create  => createAggregate(cmd)
        case cmd: AddItem => addItem(cmd)
        case cmd: AnotherAddCommand => addAnother(cmd)
        case cmd: Stop =>
          logger.debug("Stopping Aggregate {}", cmd.aggregateRootId)
          Effect.stop().thenReply(cmd.replyTo)(_ => OK)
        case cmd: StopAfter =>
          timers.startSingleTimer(
            InternalStop(aggregateRootId = cmd.aggregateRootId, metaData = cmd.metaData),
            2.seconds
          )
          Effect.none.thenReply(cmd.replyTo)(_ => OK)
        case cmd: InternalStop =>
          logger.debug("InternalStop for aggregate {}", cmd.aggregateRootId)
          Effect.stop().thenNoReply()
      }
  }

  private def createAggregate(cmd: Create): ReplyEffect[SimpleAggregateEvent, AnotherAggregateState] = {
    logger.debug(s"Create Aggregate (replayed: $replayed)" + cmd)
    Effect.persist(Created(cmd.aggregateRootId, TestMetaData.fromCommand(cmd.metaData))).thenReply(cmd.replyTo)(_ ⇒ OK)
  }

  private def addItem(cmd: AddItem): ReplyEffect[SimpleAggregateEvent, AnotherAggregateState] = {
    logger.debug("AddItem " + cmd)
    Effect
      .persist(ItemAdded(cmd.aggregateRootId, TestMetaData.fromCommand(cmd.metaData), cmd.item))
      .thenReply(cmd.replyTo)(_ ⇒ OK)
  }

  private def addAnother(cmd: AnotherAddCommand): ReplyEffect[SimpleAggregateEvent, AnotherAggregateState] = {
    logger.debug("AddItem " + cmd)
    Effect
      .persist(AnotherAdded(cmd.aggregateRootId, TestMetaData.fromCommand(cmd.metaData), cmd.item))
      .thenReply(cmd.replyTo)(_ ⇒ OK)
  }

  // event handler to keep internal aggregate state
  val eventHandler: (AnotherAggregateState, SimpleAggregateEvent) => AnotherAggregateState = { (state, event) =>
    logger.debug("updating state with {}", event)
    state.update(event)
  }

}

class AnotherAggregateManager() extends TypedAggregateRootManager[TypedSimpleAggregate.SimpleAggregateCommand] {
  final val logger = Logger(this.getClass.getSimpleName)

  override def behavior(id: String): Behavior[TypedSimpleAggregate.SimpleAggregateCommand] = {
    logger.debug("Create Another aggregate behavior for {}", id)
    Behaviors.withTimers { timers ⇒
      EventSourcedBehavior
        .withEnforcedReplies(
          PersistenceId(TypedAnotherAggregate.aggregateRootTag, id),
          TypedAnotherAggregate.AnotherAggregateState(List.empty[String]),
          TypedAnotherAggregate.commandHandler(timers),
          TypedAnotherAggregate.eventHandler
        )
        .receiveSignal {
          case (state, b: RecoveryCompleted) => {
            logger.debug("Received RecoveryCompleted for actor with state {}", state)
            TypedAnotherAggregate.replayed = true
          }
        }
    }
  }

  override def entityTypeKey: EntityTypeKey[SimpleAggregateCommand] =
    EntityTypeKey[SimpleAggregateCommand](TypedAnotherAggregate.aggregateRootTag)
}
