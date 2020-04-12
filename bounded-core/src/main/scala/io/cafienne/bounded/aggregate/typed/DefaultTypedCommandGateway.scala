/*
 * Copyright (C) 2016-2020 Cafienne B.V. <https://www.cafienne.io/bounded>
 */

package io.cafienne.bounded.aggregate.typed

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Props, Scheduler, SpawnProtocol, Terminated}
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import io.cafienne.bounded.aggregate.{CommandValidator, DomainCommand, ReplyTo, ValidateableCommand}
import akka.actor.typed.scaladsl.adapter._

import scala.concurrent.{ExecutionContext, Future}

trait TypedCommandGateway[T <: DomainCommand] {
  def sendAndAsk(command: T)(implicit validator: ValidateableCommand[T]): Future[_]
  def send(command: T)(implicit validator: ValidateableCommand[T]): Future[_]
}

class DefaultTypedCommandGateway[Cmd <: DomainCommand](
  system: ActorSystem[_],
  aggregateRootCreator: TypedAggregateRootCreator[Cmd]
)(implicit timeout: Timeout, ec: ExecutionContext, scheduler: Scheduler)
    extends TypedCommandGateway[Cmd] {

  import akka.actor.typed.scaladsl.AskPattern._
  import akka.actor.typed.scaladsl.adapter._

  object RootBehavior {
    def apply(): Behavior[SpawnProtocol.Command] =
      Behaviors
        .setup[SpawnProtocol.Command] { context =>
          SpawnProtocol()
        }
  }

  val gateway    = system.systemActorOf(RootBehavior(), "typedcommandgateway")
  val aggregates = collection.mutable.Map[String, ActorRef[Cmd]]()

  override def sendAndAsk(command: Cmd)(implicit validator: ValidateableCommand[Cmd]): Future[_] = {

//    CommandValidator
//      .validate(command)
//      .map(
//        validatedCommand => getAggregateRoot(validatedCommand.aggregateRootId).ask(ref => command)
//      )
    Future.failed(new RuntimeException("Please use send with a ReplyBehavior in your protocol"))
  }

  override def send(command: Cmd)(implicit validator: ValidateableCommand[Cmd]): Future[_] = {
    system.log.debug("Received send {}", command)
    CommandValidator
      .validate(command)
      .flatMap { validatedCommand =>
        aggregates
          .get(command.aggregateRootId)
          .fold(spawnAggregateRoot(validatedCommand.aggregateRootId).map { ar =>
            aggregates.update(command.aggregateRootId, ar)
            ar.tell(validatedCommand)
          })(found => Future { found.tell(command) })
      }
  }

  private def spawnAggregateRoot(aggregateRootId: String): Future[ActorRef[Cmd]] = {
    gateway.ask(
      SpawnProtocol.Spawn(
        behavior = aggregateRootCreator.behavior(aggregateRootId),
        name = aggregateRootId,
        props = Props.empty,
        _
      )
    )
  }
}
