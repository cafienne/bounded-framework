/*
 * Copyright (C) 2016-2020 Cafienne B.V. <https://www.cafienne.io/bounded>
 */

package io.cafienne.bounded.aggregate.typed

import akka.actor.typed.Behavior
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import io.cafienne.bounded.aggregate.DomainCommand

trait TypedAggregateRootCreator[T <: DomainCommand] {
  def behavior(id: String): Behavior[T]
  def entityTypeKey: EntityTypeKey[_]
}
