/*
 * Copyright (C) 2016-2020 Cafienne B.V. <https://www.cafienne.io/bounded>
 */

package io.cafienne.bounded.test.typed

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.{ActorRef, Scheduler}
import akka.util.Timeout
import io.cafienne.bounded.aggregate.typed.{TypedAggregateRootCreator, TypedCommandGateway}
import io.cafienne.bounded.aggregate.{DomainCommand, ValidateableCommand}

import scala.concurrent.{ExecutionContext, Future}

class TestTypedCommandGateway[T <: DomainCommand](
  testKit: ActorTestKit,
  aggregateRootCreator: TypedAggregateRootCreator[T]
)(
  implicit timeout: Timeout,
  ec: ExecutionContext,
  scheduler: Scheduler
) extends TypedCommandGateway[T] {
  import akka.actor.typed.scaladsl.AskPattern._

  override def ask[Res](aggregateRootId: String, replyTo: ActorRef[Res] => T)(
    implicit validator: ValidateableCommand[T]
  ): Future[Res] = {
    val aggregateRoot = testKit.spawn(aggregateRootCreator.behavior(aggregateRootId))
    aggregateRoot.ask(replyTo)
  }

  override def tell(command: T)(implicit validator: ValidateableCommand[T]): Future[_] = {
    val aggregateRoot = testKit.spawn(aggregateRootCreator.behavior(command.aggregateRootId))
    Future { aggregateRoot.tell(command) }
  }

  override def shutdown(): Future[Unit] = {
    Future.successful()
  }

}
