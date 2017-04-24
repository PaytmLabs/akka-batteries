package com.paytmlabs.akka.cluster.discovery.network

import scala.collection.JavaConverters.asJavaCollectionConverter
import scala.collection.JavaConverters.asScalaBufferConverter

import com.paytmlabs.akka.commons.util.ConfigUtils.RichConfig

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Cancellable
import akka.actor.Props
import akka.cluster.Cluster
import scala.collection.immutable.SortedSet
import scala.util.Try

object NetworkActor {
  def props = Props(classOf[NetworkActor])
  val provider = "network"
}

class NetworkActor extends Actor with ActorLogging {
  import NetworkActor._
  import context.dispatcher
  import com.paytmlabs.akka.cluster.discovery.SeedActor._

  val conf = context.system.settings.config.getConfig("akka.cluster")
  val discoveryConf = conf.getConfig("discovery")
  val interval = discoveryConf.getFiniteDuration("search-interval")
  val seedNodes = SortedSet[String]() ++ discoveryConf.getStringList("modules.network.seed-hosts").asScala.toSet
  val minSeeds = conf.getInt("role.seed.min-nr-of-members")

  var timer: Option[Cancellable] = None

  val eventStream = context.system.eventStream
  val cluster = Cluster(context.system)

  eventStream.subscribe(self, DiscoverCluster.getClass)

  cluster.registerOnMemberUp {
    log.info("Cluster is UP")
    timer.foreach(_.cancel)
    eventStream.unsubscribe(self)
    context.stop(self)
  }

  override def preStart(): Unit = {
    log.info(s"Starting NetworkActor with seeds $seedNodes")
  }

  override def postStop(): Unit = {
    log.info(s"${this.getClass.getSimpleName} stopped successfully")
  }

  override def receive = {
    case Tick =>
      if (seedNodes.size >= minSeeds)
        eventStream.publish(DiscoveredCluster(seedNodes, provider))
      else
        log.info("Insufficient seed nodes found {}, required {}", seedNodes, minSeeds)

    case DiscoverCluster =>
      if (timer.isEmpty) {
        log.info("Starting Network discovery ...")
        timer = Some(context.system.scheduler.schedule(interval, interval, self, Tick))
      }
  }
}
