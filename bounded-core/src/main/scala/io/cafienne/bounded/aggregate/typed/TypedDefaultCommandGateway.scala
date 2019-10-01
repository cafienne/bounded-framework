/*
 * Copyright (C) 2016-2019 Cafienne B.V. <https://www.cafienne.io/bounded>
 */

package io.cafienne.bounded.aggregate.typed

import akka.actor.{ActorSystem, Scheduler}
import akka.util.Timeout
import io.cafienne.bounded.aggregate.{
  AggregateRootCreator,
  AggregateRootId,
  CommandGateway,
  CommandValidator,
  DomainCommand,
  ValidateableCommand
}

import scala.concurrent.Future

trait TypedCommandGateway[T <: DomainCommand] {
  def sendAndAsk(command: T)(implicit validator: ValidateableCommand[T]): Future[_]
  def send(command: T)(implicit validator: ValidateableCommand[T]): Future[Unit]
}

class TypedDefaultCommandGateway[Cmd <: DomainCommand](
  system: ActorSystem,
  aggregateRootCreator: TypedAggregateRootCreator[Cmd]
)(implicit timeout: Timeout, scheduler: Scheduler)
    extends TypedCommandGateway[Cmd] {
  import akka.actor.typed.ActorRef
  import akka.actor.typed.scaladsl.adapter._
  import akka.actor.typed.scaladsl.AskPattern._

  implicit val actorSystem = system
  implicit val ec          = actorSystem.dispatcher

  override def sendAndAsk(command: Cmd)(implicit validator: ValidateableCommand[Cmd]): Future[_] = {
    CommandValidator
      .validate(command)
      .flatMap(validatedCommand => getAggregateRoot(validatedCommand.aggregateRootId).ask(ref => validatedCommand))
  }

  override def send(command: Cmd)(implicit validator: ValidateableCommand[Cmd]): Future[Unit] = {
    CommandValidator
      .validate(command)
      .map(validatedCommand => getAggregateRoot(validatedCommand.aggregateRootId).tell(validatedCommand))
  }

  private def getAggregateRoot(t: AggregateRootId): ActorRef[Cmd] = {
    system.spawn(aggregateRootCreator.behavior(t), t.idAsString)
  }

}
