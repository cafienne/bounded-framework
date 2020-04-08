/*
 * Copyright (C) 2016-2020 Cafienne B.V. <https://www.cafienne.io/bounded>
 */

package io.cafienne.bounded.aggregate.typed

import akka.actor.Scheduler
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import akka.persistence.typed.PersistenceId
import akka.util.Timeout
import io.cafienne.bounded.aggregate.{CommandValidator, DomainCommand, ValidateableCommand}

import scala.concurrent.{ExecutionContext, Future}

trait TypedCommandGateway[T <: DomainCommand] {
  def sendAndAsk(command: T)(implicit validator: ValidateableCommand[T]): Future[_]
  def send(command: T)(implicit validator: ValidateableCommand[T]): Future[_]
}

class TypedDefaultCommandGateway[Cmd <: DomainCommand](
  clusterSharding: ClusterSharding,
  aggregateRootCreator: TypedAggregateRootCreator[Cmd]
)(implicit timeout: Timeout, scheduler: Scheduler, ec: ExecutionContext)
    extends TypedCommandGateway[Cmd] {

  //import akka.actor.typed.scaladsl.AskPattern._

  override def sendAndAsk(command: Cmd)(implicit validator: ValidateableCommand[Cmd]): Future[_] = {
//    CommandValidator
//      .validate(command)
//      .map(validatedCommand => getAggregateRoot(validatedCommand.aggregateRootId).?(validatedCommand))
    Future.successful(None)
  }

  override def send(command: Cmd)(implicit validator: ValidateableCommand[Cmd]): Future[_] = {
//    CommandValidator
//      .validate(command)
//      .map(validatedCommand => getAggregateRoot(validatedCommand.aggregateRootId).tell(validatedCommand))
    Future.successful(None)
  }

  private def getAggregateRoot(t: PersistenceId): EntityRef[_] = {
    clusterSharding.entityRefFor(aggregateRootCreator.entityTypeKey, t.id)
  }

}
