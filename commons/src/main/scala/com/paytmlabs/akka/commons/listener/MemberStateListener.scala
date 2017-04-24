package com.paytmlabs.akka.commons.listener

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.ClusterDomainEvent
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.InitialStateAsEvents
import akka.cluster.ClusterEvent.LeaderChanged
import akka.cluster.ClusterEvent.MemberEvent
import akka.cluster.ClusterEvent.ReachableMember
import akka.cluster.ClusterEvent.UnreachableMember

class MemberStateListener extends Actor with ActorLogging {

  val cluster = Cluster(context.system)

  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[ClusterDomainEvent])
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case m: UnreachableMember   => log.warning("{}", m)
    case m: ReachableMember     => log.info("{}", m)
    case m: MemberEvent         => log.info("{}", m)
    case l: LeaderChanged       => log.info("{}", l)
    case s: CurrentClusterState => log.info("{}", s)
    case _: ClusterDomainEvent  => // ignore
  }
}
