/*
 * Copyright (C) 2016-2020 Cafienne B.V. <https://www.cafienne.io/bounded>
 */

package io.cafienne.bounded.aggregate

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import scala.concurrent.Future

class TypedCommandGatewaySpec extends ScalaTestWithActorTestKit(s"""
    //akka.actor.provider = "cluster"
    //akka.cluster.seed-nodes = ["akka://TypedCommandGatewaySpec@127.0.0.1:2552"]
    akka.persistence.publish-plugin-commands = on
    akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
    akka.persistence.journal.inmem.test-serialization = on
    # snapshot store plugin is NOT defined, things should still work
    akka.persistence.snapshot-store.local.dir = "target/snapshots-${classOf[TypedCommandGatewaySpec].getName}/"
    """) with AnyWordSpecLike with Matchers {

  //import akka.actor.typed.scaladsl.adapter._
  import TypedSimpleAggregate._
  //import akka.actor.typed.scaladsl.AskPattern._

  implicit val commandValidator = new ValidateableCommand[SimpleAggregateCommand] {
    override def validate(cmd: SimpleAggregateCommand): Future[SimpleAggregateCommand] =
      Future.successful(cmd)
  }

  implicit val ec = system.executionContext

  val creator = new SimpleAggregateCreator()
//  val sharding = ClusterSharding(system)

//  sharding.init(
//    Entity(
//      typKey = creator.entityTypeKey,
//      createBehavior = entityContext => creator.behavior(entityContext.entityId)
//    )
//  )
//    "Check if the Typed Actor works with ask" in {
//      import akka.actor.typed.scaladsl.AskPattern._
//
//      val aggregateId = SimpleAggregateId("test00")
//      val creator     = new SimpleAggregateCreator()
//      val actorRef    = spawn(creator.behavior(aggregateId.idAsString), "testactor2")
//
//      val result = actorRef.ask(ref => Create(aggregateId, commandMetaData)(ref))
//      result.onComplete {
//        case Success(value) =>
//          if (value.isInstanceOf[TypedSimpleAggregate.OK.type]) {
//            logger.debug("Got " + value)
//          } else {
//            fail("received wrong response: " + value)
//          }
//        case Failure(exception) => throw exception
//      }
//    }
//    "Send a command via the gateway and wait for a Typed reply" in {
//
//      val aggregateId = SimpleAggregateId("test2")
//      val cmdGateway =
//        new TypedDefaultCommandGateway[SimpleAggregateCommand[Response]](sharding, creator)
//      val probe = testKit.createTestProbe[Response]()
//
//      val result = cmdGateway.sendAndAsk(Create(aggregateId, commandMetaData)(probe.ref))
//      result.onComplete {
//        case Success(value)     => logger.debug("Got " + value)
//        case Failure(exception) => throw exception
//      }
//      probe.expectMessage(OK)
//    }

//    "Send a command and let it go" in {
//      Behaviors.setup { ctx =>
//        val aggregateId = SimpleAggregateId("test2")
//        val cmdGateway =
//          new TypedDefaultCommandGateway[SimpleAggregateCommand[Response]](ctx, aggregateCreator)
//        val probe = testKit.createTestProbe[Response]()
//
//        val result = cmdGateway.send(Create(aggregateId, commandMetaData)(probe.ref))
//        result.onComplete {
//          case Success(value)     => logger.debug("Got " + value)
//          case Failure(exception) => throw exception
//        }
//        probe.expectMessage(OK)
//      }
//    }

}
