package com.paytmlabs.akka.commons.util

import akka.actor.ActorSystem
import scala.concurrent.Await
import akka.cluster.Cluster
import scala.util.Try
import scala.concurrent.duration.DurationInt
import akka.actor.ActorPath
import akka.actor.Address
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object ClusterUtils {

  def toSeedNodes(hosts: Seq[String], actorSystemName: String, protocol: String): collection.immutable.Seq[Address] = {
    hosts.map {
      path =>
        ActorPath.fromString(s"$protocol://$actorSystemName@$path").address
    }.toList
  }
  
  def registerOnUnexpectedShutdown(system: ActorSystem): Unit = {

    val cluster = Cluster(system)
    system.log.info("Leaving the cluster {}", cluster.selfAddress)

    val leaveTimeout = 5 seconds
    val leaveLatch = new CountDownLatch(1)
    cluster.registerOnMemberRemoved {
      leaveLatch.countDown()
    }

    cluster.leave(cluster.selfAddress)

    if (!leaveLatch.await(leaveTimeout.toMillis, TimeUnit.MILLISECONDS)) {
      system.log.warning("Not sure if I left the cluster")
    }

    system.log.info("Shutting down ActorSystem, uptime {}", system.uptime)
    val terminateTimeout = 5 seconds
    val terminateLatch = new CountDownLatch(1)
    system.registerOnTermination {
      terminateLatch.countDown()
    }

    system.terminate()

    if (!leaveLatch.await(terminateTimeout.toMillis, TimeUnit.MILLISECONDS)) {
      println("WARN: Force exsiting the JVM")
      sys.exit(1)
    }
  }
  
  def shutdownOnMemberRemoved(system: ActorSystem)(callback: => Unit) = {
    Cluster(system).registerOnMemberRemoved {
      system.registerOnTermination({
        callback
        sys.exit(129)
      })

      system.terminate()

      // To not block the shutdown thread
      new Thread {
        override def run(): Unit = {
          if (Try(Await.ready(system.whenTerminated, 10 seconds)).isFailure)
            println("WARN: Force exsiting the JVM")
            sys.exit(1)
        }
      }.start()
    }
  }

}