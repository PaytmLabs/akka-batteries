package com.paytmlabs.akka.cluster.router

import scala.collection.immutable

import com.typesafe.config.Config

import akka.actor.ActorSystem
import akka.actor.Address
import akka.cluster.Cluster
import akka.dispatch.Dispatchers
import akka.japi.Util.immutableSeq
import akka.routing.ActorRefRoutee
import akka.routing.ActorSelectionRoutee
import akka.routing.Group
import akka.routing.RoundRobinRoutingLogic
import akka.routing.Routee
import akka.routing.Router
import akka.routing.RoutingLogic
import akka.routing.NoRoutee
import akka.routing.RandomRoutingLogic
import akka.routing.ConsistentHashingRoutingLogic

final class LocalAffinityRoutingLogic(system: ActorSystem, routingLogic: RoutingLogic ) extends RoutingLogic {
  val cluster = Cluster(system)
  val log = system.log

  def select(message: Any, routees: immutable.IndexedSeq[Routee]): Routee = {

    val target = routees.find(hasLocalAffinity(_)).
      getOrElse(routingLogic.select(message, routees))

    target
  }

  private def hasLocalAffinity(routee: Routee): Boolean = routee match {
    case NoRoutee  => false
    case _: Routee => fullAddress(routee).host == cluster.selfAddress.host
  }

  private def fullAddress(routee: Routee): Address = {
    val address = routee match {
      case ActorRefRoutee(ref)       => ref.path.address
      case ActorSelectionRoutee(sel) => sel.anchorPath.address
    }
    address match {
      case Address(_, _, None, None) => cluster.selfAddress
      case addr                      => addr
    }
  }
}

final case class LocalAffinityRoundRobinGroup(routeePaths: immutable.Iterable[String]) extends Group {

  def this(config: Config) = this(
    routeePaths = immutableSeq(config.getStringList("routees.paths")))

  override def paths(system: ActorSystem): immutable.Iterable[String] = routeePaths

  override def createRouter(system: ActorSystem): Router =
    new Router(new LocalAffinityRoutingLogic(system, RoundRobinRoutingLogic()))
  akka.routing.ConsistentHashingRoutingLogic
  akka.routing.SmallestMailboxRoutingLogic
  override val routerDispatcher: String = Dispatchers.DefaultDispatcherId
}

final case class LocalAffinityRandomGroup(routeePaths: immutable.Iterable[String]) extends Group {

  def this(config: Config) = this(
    routeePaths = immutableSeq(config.getStringList("routees.paths")))

  override def paths(system: ActorSystem): immutable.Iterable[String] = routeePaths

  override def createRouter(system: ActorSystem): Router =
    new Router(new LocalAffinityRoutingLogic(system, RandomRoutingLogic()))

  override val routerDispatcher: String = Dispatchers.DefaultDispatcherId
}