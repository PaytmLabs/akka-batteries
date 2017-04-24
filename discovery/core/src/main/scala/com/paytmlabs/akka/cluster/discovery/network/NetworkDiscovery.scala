package com.paytmlabs.akka.cluster.discovery.network

import akka.actor.ExtensionIdProvider
import akka.actor.ExtensionId
import akka.actor.ExtendedActorSystem
import akka.event.Logging
import com.paytmlabs.akka.cluster.discovery.Discovery
import com.paytmlabs.akka.cluster.discovery.SeedActor
import akka.cluster.Cluster

object NetworkDiscovery extends ExtensionId[NetworkExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Discovery.Extension] = NetworkDiscovery
  override def createExtension(system: ExtendedActorSystem): NetworkExtension = new NetworkExtension(system)
}

class NetworkExtension(override val system: ExtendedActorSystem) extends Discovery.Extension {
  val log = Logging(system, classOf[NetworkExtension])
  log.info("NetworkExtension created")

  override def start(): Boolean = {
    val discoveryConf = system.settings.config.getConfig("akka.cluster.discovery")
    if (discoveryConf.getBoolean("enabled")) {
      startSeedActor()
      system.actorOf(NetworkActor.props, "NetworkActor")
      true
    } else false
  }
}