package com.paytmlabs.akka.cluster.sbr

import scala.annotation.migration
import scala.collection.immutable
import scala.concurrent.duration.Deadline
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Address
import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.ClusterEvent
import akka.cluster.ClusterEvent.ClusterDomainEvent
import akka.cluster.ClusterEvent.LeaderChanged
import akka.cluster.ClusterEvent.MemberRemoved
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.ClusterEvent.ReachableMember
import akka.cluster.ClusterEvent.UnreachableMember
import akka.cluster.Member
import akka.cluster.MemberStatus
import akka.cluster.MemberStatus.Down
import akka.cluster.MemberStatus.Exiting
import akka.cluster.UniqueAddress

object RoleBasedSplitBrainResolver {
  def props(stableAfter: FiniteDuration, essentialRoles: Set[String]): Props =
    Props(classOf[RoleBasedSplitBrainResolver], stableAfter, essentialRoles)

  sealed trait DownAction
  case object DownReachable extends DownAction
  case object DownUnreachable extends DownAction
  case object DownAll extends DownAction

  case object Tick
}

class RoleBasedSplitBrainResolver(stableAfter: FiniteDuration, essentialRoles: Set[String]) extends Actor with ActorLogging {

  import RoleBasedSplitBrainResolver._
  import context.dispatcher

  val cluster = Cluster(context.system)

  val selfUniqueAddress = cluster.selfUniqueAddress

  val tickTask = {
    val interval = (stableAfter / 2).max(500 millis)
    context.system.scheduler.schedule(interval, interval / 2, self, Tick)
  }

  val ignoreMemberStatus = Set[MemberStatus](Down, Exiting)

  var isLeader = false

  var selfAdded = false

  def resetStableDeadline(): Deadline = Deadline.now + stableAfter

  var stableDeadline = resetStableDeadline()

  var unreachable = Set.empty[UniqueAddress]

  private var members = immutable.SortedSet.empty(Member.ordering)

  def nodesAddress = members.map(_.uniqueAddress)

  def reachableNodesAddress = members.collect {
    case m if !unreachable(m.uniqueAddress) => m.uniqueAddress
  }

  def unreachableMembers = members.filter(m => unreachable(m.uniqueAddress))

  def reachableMembers = members.filter(m => !unreachable(m.uniqueAddress))

  override def preStart(): Unit = {
    cluster.subscribe(self, ClusterEvent.InitialStateAsEvents, classOf[ClusterDomainEvent])
    super.preStart()
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    tickTask.cancel()
    super.postStop()
  }

  def down(node: Address): Unit = {
    require(isLeader, "Must be a cluster leader to down nodes")
    cluster.down(node)
  }

  def joining: Set[UniqueAddress] = cluster.state.members.collect {
    case m if m.status == MemberStatus.Joining => m.uniqueAddress
  }

  def decide(): DownAction = {

    val unreachableOK = essentialRoles subsetOf unreachableMembers.flatMap(_.roles)
    val reachableOK = essentialRoles subsetOf reachableMembers.flatMap(_.roles)

    log.info("unreachableOK: {}, reachableOK: {}", unreachableOK, reachableOK)

    if (unreachableOK && reachableOK) {
      // If both sides contains essential roles, then apply keep majority strategy
      val unreachableSize = unreachableMembers.size
      val membersSize = members.size
      log.info("unreachableSize: {}, membersSize: {}", unreachableSize, membersSize)

      if (unreachableSize * 2 == membersSize) {
        log.info("Both partitions are equal in size, break the tie by keeping the side with oldest member")
        if (unreachable(members.head.uniqueAddress)) DownReachable else DownUnreachable
      } else if (unreachableSize * 2 < membersSize) {
        log.info("We are in majority, DownUnreachable")
        DownUnreachable
      } else {
        log.info("We are in minority, DownReachable")
        DownReachable
      }
    } else if (unreachableOK) {
      log.info("Only unreachable nodes have essential roles, DownReachable")
      DownReachable
    } else if (reachableOK) {
      log.info("Only we have essential roles, DownUnreachable")
      DownUnreachable
    } else {
      log.info("No side has essential roles, DownAll")
      DownAll
    }

  }

  def receive = {
    case UnreachableMember(m) => unreachableMember(m)
    case ReachableMember(m)   => reachableMember(m)
    case MemberUp(m)          => memberUp(m)
    case MemberRemoved(m, _)  => memberRemoved(m)

    case LeaderChanged(leaderOption) =>
      isLeader = leaderOption.contains(selfUniqueAddress.address)

    case Tick =>

      val shouldAct = isLeader && selfAdded && unreachable.nonEmpty && stableDeadline.isOverdue()

      if (shouldAct) {

        val downAction = decide()

        val nodesToDown = downAction match {
          case DownUnreachable => unreachable
          case DownReachable   => reachableNodesAddress ++ joining.filterNot(unreachable)
          case DownAll         => nodesAddress ++ joining
        }

        if (nodesToDown.nonEmpty) {

          val downSelf = nodesToDown.contains(selfUniqueAddress)

          log.info("downAction: {}, nodesToDown: [{}], downSelf: {}, (unreachable.size, members.size): {}", downAction,
            nodesToDown.map(_.address).mkString(", "), downSelf, (unreachable.size, members.size))

          nodesToDown.foreach(node ⇒ if (node != selfUniqueAddress) down(node.address))

          if (downSelf)
            down(selfUniqueAddress.address)

          stableDeadline = resetStableDeadline()
        }
      }

    case _: ClusterDomainEvent ⇒ // ignore

  }

  def unreachableMember(m: Member): Unit = {
    require(m.uniqueAddress != selfUniqueAddress, "selfAddress cannot be unreachable")

    // Ignore Down and Exiting nodes
    if (!ignoreMemberStatus(m.status)) {
      unreachable += m.uniqueAddress

      if (m.status != MemberStatus.Joining)
        add(m)
    }

    stableDeadline = resetStableDeadline()
  }

  def reachableMember(m: Member): Unit = {
    unreachable -= m.uniqueAddress

    stableDeadline = resetStableDeadline()
  }

  def memberUp(m: Member): Unit = {

    add(m)

    if (m.uniqueAddress == selfUniqueAddress)
      selfAdded = true

    stableDeadline = resetStableDeadline()
  }

  def memberRemoved(m: Member): Unit = {

    if (m.uniqueAddress == selfUniqueAddress) {
      context.stop(self)
    } else {
      unreachable -= m.uniqueAddress
      members -= m
      stableDeadline = resetStableDeadline()
    }
  }

  def add(m: Member): Unit = {
    // Replace if exists, default comparison is based on address not uniqueAddress
    members = members - m + m
  }
}