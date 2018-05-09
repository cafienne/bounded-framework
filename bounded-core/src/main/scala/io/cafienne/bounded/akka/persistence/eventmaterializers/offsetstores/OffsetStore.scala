/*
 * Copyright (C) 2016-2018 Cafienne B.V. <https://www.cafienne.io/bounded>
 */

package io.cafienne.bounded.akka.persistence.eventmaterializers.offsetstores

import akka.persistence.query.Offset

import scala.concurrent.Future

final case class EventNumber(value: Int) //extends Offset

trait OffsetStore {

  def saveOffset(viewIdentifier: String, offset: Offset): Future[Unit] = ???

  def getOffset(viewIdentifier: String): Future[Offset] = ???

}
