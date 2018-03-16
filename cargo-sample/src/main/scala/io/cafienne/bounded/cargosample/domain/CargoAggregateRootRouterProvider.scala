/**
  * Copyright (C) 2016-2018 Cafienne B.V. <https://www.cafienne.io/bounded>
  */
package io.cafienne.bounded.cargosample.domain

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }
import akka.util.Timeout
import io.cafienne.bounded.aggregate.{ AggregateRootId, CommandValidationException, CommandValidator }
import io.cafienne.bounded.cargosample.domain.CargoDomainProtocol._
import scala.util.{ Failure, Success }

trait CargoAggregateRootRouterProvider {
  def router(): ActorRef
}

object InMemRoutingCargoRouterProvider extends CargoAggregateRootRouterProvider {
  private var inMemoryRouter: Option[ActorRef] = None

  override def router(): ActorRef = {
    inMemoryRouter.get
  }

  def apply = throw new IllegalArgumentException("Can only be created with an actorsystem as argument")

  def apply(system: ActorSystem) = {
    inMemoryRouter = Some(system.actorOf(InMemCargoAggregateRootRouter.props(new CargoCommandValidatorsImpl(system)), name = "InMemRoutingCargoRouterProvider"))
    this
  }
}

class InMemCargoAggregateRootRouter(validators: CargoCommandValidators) extends Actor with ActorLogging {

  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.duration._
  import validators._

  implicit val system = context.system
  implicit val timeout = Timeout(3.seconds)

  //TODO normal Routee functionality + sleep of actors that were not used for a while
  val aggregateRootInstanceActors = collection.mutable.Map[AggregateRootId, ActorRef]()

  private def getAggregateRoot(c: CargoDomainCommand)(implicit system: ActorSystem): ActorRef = {
    aggregateRootInstanceActors.getOrElseUpdate(c.id, system.actorOf(Cargo.props(c.id)))
  }

  //TODO see if there is a way to keep the specific Type when matching on a base type (using TypeTag ?)
  // see http://stackoverflow.com/questions/18819924/preserving-type-information-in-akka-receive
  // solution with typetag should not exceed the complexity of handing specific commands over here.
  def receive: Actor.Receive = {
    case c: PlanCargo =>
      val originalSender = sender()
      CommandValidator.validate(c) onComplete {
        case Success(validated: CargoDomainCommand) =>
          getAggregateRoot(validated).tell(validated, originalSender)
        case Failure(err) => {
          originalSender ! Left(CommandValidationException(s"Could not validate $c", err))
        }
      }

    case c: SpecifyNewRoute =>
      val originalSender = sender()
      CommandValidator.validate(c) onComplete {
        case Success(validated: CargoDomainCommand) =>
          getAggregateRoot(validated).tell(validated, originalSender)
        case Failure(err) => originalSender ! Left(CommandValidationException(s"Could not validate $c", err))
      }

    case other =>
      log.info("The Cargo aggregate root router received an unknown command: " + other)
      sender() ! Left(new IllegalArgumentException(s"The Cargo aggregate root router received an unknown command: $other"))
  }
}

object InMemCargoAggregateRootRouter {
  def props(validators: CargoCommandValidators): Props = {
    Props(classOf[InMemCargoAggregateRootRouter], validators)
  }
}
