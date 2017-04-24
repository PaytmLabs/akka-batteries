package com.paytmlabs.akka.cluster.discovery.elb

import scala.collection.JavaConverters.asJavaCollectionConverter
import scala.collection.JavaConverters.asScalaBufferConverter

import com.amazonaws.services.ec2.AmazonEC2ClientBuilder
import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClientBuilder
import com.amazonaws.services.elasticloadbalancing.model.DescribeInstanceHealthRequest
import com.paytmlabs.akka.commons.util.ConfigUtils.RichConfig

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Cancellable
import akka.actor.Props
import akka.cluster.Cluster
import scala.collection.immutable.SortedSet
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest
import scala.util.Try

object ELBActor {
  def props = Props(classOf[ELBActor])
  val provider = "elb"

  /**
   * Query the given ELB and return the ip:port of all InService instances
   */
  def queryELB(elbName: String, inServiceOnly: Boolean): Try[Option[Seq[String]]] = Try {

    // Get the seed node port from ELB
    val describeLoadBalancersRequest = new DescribeLoadBalancersRequest()
    describeLoadBalancersRequest.setLoadBalancerNames(List(elbName).asJavaCollection)
    val elbClient = AmazonElasticLoadBalancingClientBuilder.defaultClient()
    val loadBalancers = elbClient.describeLoadBalancers(describeLoadBalancersRequest).getLoadBalancerDescriptions.asScala.toList

    val port: Integer = loadBalancers.head.getListenerDescriptions.asScala.toList.head.getListener.getInstancePort

    // Get the private IP of InService seed instances behind the ELB
    val describeInstanceHealthRequest = new DescribeInstanceHealthRequest()
    describeInstanceHealthRequest.setLoadBalancerName(elbName)

    val elbInstances = elbClient.describeInstanceHealth(describeInstanceHealthRequest).getInstanceStates().asScala.toList
    elbClient.shutdown()
    val seedInstances = if (inServiceOnly) elbInstances.filter(_.getState == "InService") else elbInstances
    if (!seedInstances.isEmpty) {
      val ids = seedInstances.map(_.getInstanceId)
      val describeInstancesRequest = new DescribeInstancesRequest()
      describeInstancesRequest.setInstanceIds(ids.asJavaCollection)
      val ec2Client = AmazonEC2ClientBuilder.defaultClient()
      val ec2Instances = ec2Client.describeInstances(describeInstancesRequest)
      ec2Client.shutdown()
      val reservations = ec2Instances.getReservations().asScala.toList
      if (!reservations.isEmpty) {
        // Finally, return seeds address Seq[ip:port]
        Some(reservations.flatMap(_.getInstances.asScala.toList.map { i => s"${i.getPrivateIpAddress}:$port" }))
      } else None // Missing EC2 instance description
    } else None // Nothing InService
  }

}

class ELBActor extends Actor with ActorLogging {
  import ELBActor._
  import context.dispatcher
  import com.paytmlabs.akka.cluster.discovery.SeedActor._

  val conf = context.system.settings.config.getConfig("akka.cluster")
  val discoveryConf = conf.getConfig("discovery")
  val interval = discoveryConf.getFiniteDuration("search-interval")
  val elbName = discoveryConf.getString("modules.elb.name")
  val inServiceOnly = discoveryConf.getBoolean("modules.elb.in-service-only")
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
    log.info(s"Starting ELBActor for $elbName")
  }

  override def postStop(): Unit = {
    log.info(s"${this.getClass.getSimpleName} stopped successfully")
  }

  override def receive = {
    case Tick =>
      val queryResult = queryELB(elbName, inServiceOnly)
      log.info(s"ELB queryResult: $queryResult")
      if (queryResult.isSuccess) {
        queryResult.get match {
          case Some(seedNodes) if seedNodes.size >= minSeeds =>
            val nodes = SortedSet[String]() ++ seedNodes.toSet
            eventStream.publish(DiscoveredCluster(nodes, provider))
          case Some(seedNodes) => log.info("Insufficient seed nodes found {}, required {}", seedNodes, minSeeds)
          case None            => // ignore
        }
      }
    case DiscoverCluster =>
      if (timer.isEmpty) {
        log.info("Starting ELB discovery ...")
        timer = Some(context.system.scheduler.schedule(interval, interval, self, Tick))
      }
  }
}
