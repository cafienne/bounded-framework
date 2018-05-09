/*
 * Copyright (C) 2016-2018 Cafienne B.V. <https://www.cafienne.io/bounded>
 */

package io.cafienne.bounded.akka.persistence.eventmaterializers

import akka.persistence.query.Offset
import com.typesafe.config.Config
import io.cafienne.bounded.akka.ActorSystemProvider
import io.cafienne.bounded.akka.persistence.eventmaterializers.offsetstores.{
  InMemoryBasedOffsetStore,
  LmdbConfig,
  LmdbOffsetStore,
  OffsetStore
}

import scala.concurrent.Future

trait OffsetStoreProvider extends OffsetStore {
  this: ActorSystemProvider =>

  private val offsetStoreConfig = system.settings.config.getConfig("bounded.eventmaterializers.offsetstore")

  private val store: OffsetStore = OffsetStoreProvider.getStore(offsetStoreConfig)

  override def saveOffset(viewIdentifier: String, offset: Offset): Future[Unit] =
    store.saveOffset(viewIdentifier, offset)

  override def getOffset(viewIdentifier: String): Future[Offset] = store.getOffset(viewIdentifier)

}

object OffsetStoreProvider {

  private var store: Option[OffsetStore] = None

  def getStore(offsetStoreConfig: Config): OffsetStore = {
    if (store.isEmpty) {
      store = offsetStoreConfig.getString("type") match {
        case "lmdb" =>
          val lmdbConfig = new LmdbConfig(offsetStoreConfig)
          Some(LmdbOffsetStore(lmdbConfig))
        case "inmemory" => Some(new InMemoryBasedOffsetStore())
        case other      => throw new RuntimeException(s"Offsetstore $other is not supported by OffsetStoreProvider")
      }
    }
    store.get
  }

}
