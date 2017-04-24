package com.paytmlabs.akka.cluster.discovery

import scala.collection.immutable.SortedSet

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Address
import akka.cluster.Cluster
import akka.actor.Props
import com.paytmlabs.akka.commons.util.ClusterUtils

object SeedActor {
  def props = Props(classOf[SeedActor])

  case object Tick
  case object JoinSelf
  case object DiscoverCluster
  case class DiscoveredCluster(seedNodes: SortedSet[String], provider: String)
}

class SeedActor extends Actor with ActorLogging {
  import SeedActor._
  import context.dispatcher
  import com.paytmlabs.akka.commons.util.ConfigUtils.RichConfig

  val conf = context.system.settings.config.getConfig("akka.cluster.discovery")
  val interval = conf.getFiniteDuration("search-interval")
  val timer = context.system.scheduler.schedule(interval, interval, self, Tick)

  val eventStream = context.system.eventStream
  val cluster = Cluster(context.system)
  val selfAddress = s"${cluster.selfAddress.host}:${cluster.selfAddress.port}"

  eventStream.subscribe(self, classOf[DiscoveredCluster])
  eventStream.subscribe(self, JoinSelf.getClass)

  cluster.registerOnMemberUp {
    log.info("Cluster is UP")
    timer.cancel()
    eventStream.unsubscribe(self)
    context.stop(self)
  }

  override def preStart(): Unit = {
    log.info(s"Starting SeedActor with search interval $interval")
  }

  override def postStop(): Unit = {
    log.info(s"${this.getClass.getSimpleName} stopped successfully")
  }

  override def receive = {
    case Tick =>
      eventStream.publish(DiscoverCluster)

    case DiscoveredCluster(seedNodes, provider) =>

      // http://doc.akka.io/docs/akka/2.4.17/scala/cluster-usage.html
      // Remove the current seed node unless it is the first node in the seed list
      val seedsToJoin = seedNodes filter { address =>
        address != selfAddress || address == seedNodes.head
      }

      log.info("Cluster discovered with {}, joining {}", provider, seedsToJoin)

      cluster.joinSeedNodes(ClusterUtils.toSeedNodes(seedsToJoin.toSeq, context.system.name, cluster.selfAddress.protocol))

    case JoinSelf =>
      log.info("Cluster JoinSelf")
      cluster.join(cluster.selfAddress)
  }
}