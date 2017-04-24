package com.paytmlabs.akka.cluster.discovery

import scala.collection.JavaConverters.asJavaCollectionConverter
import scala.collection.JavaConverters.asScalaBufferConverter

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Cancellable
import akka.actor.Props
import akka.cluster.Cluster
import scala.collection.immutable.SortedSet
import scala.util.Try
import com.paytmlabs.akka.commons.util.ConfigUtils.RichConfig

object SelfJoinActor {
  def props = Props(classOf[SelfJoinActor])
  val provider = "self"
}

class SelfJoinActor extends Actor with ActorLogging {
  import SelfJoinActor._
  import context.dispatcher
  import com.paytmlabs.akka.cluster.discovery.SeedActor._

  val conf = context.system.settings.config.getConfig("akka.cluster")
  val discoveryConf = conf.getConfig("discovery")
  val interval = discoveryConf.getFiniteDuration("search-interval")

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
    log.info(s"Starting SelfJoinActor")
  }

  override def postStop(): Unit = {
    log.info(s"${this.getClass.getSimpleName} stopped successfully")
  }

  override def receive = {
    case Tick =>
      eventStream.publish(JoinSelf)

    case DiscoverCluster =>
      if (timer.isEmpty) {
        log.info("Starting SelfJoin discovery ...")
        timer = Some(context.system.scheduler.schedule(interval, interval, self, Tick))
      }
  }
}
