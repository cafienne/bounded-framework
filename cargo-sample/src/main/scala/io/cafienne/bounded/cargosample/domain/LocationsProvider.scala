package io.cafienne.bounded.cargosample.domain

import io.cafienne.bounded.cargosample.domain.CargoDomainProtocol.Location

trait LocationsProvider {

  def getLocation(input: String): Option[Location]

}

class FixedLocationsProvider extends LocationsProvider {

  val locations = Map("Oslo" -> Location("Oslo"), "Amsterdam" -> Location("Amsterdam"))

  def getLocation(input: String) = locations.get(input)

}

object FixedLocationsProvider {

  private val fixedLocationsProvider = new FixedLocationsProvider()

  def apply(): LocationsProvider = fixedLocationsProvider

}
