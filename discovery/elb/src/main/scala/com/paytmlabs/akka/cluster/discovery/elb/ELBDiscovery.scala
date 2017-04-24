package com.paytmlabs.akka.cluster.discovery.elb

import akka.actor.ExtensionIdProvider
import akka.actor.ExtensionId
import akka.actor.ExtendedActorSystem
import akka.event.Logging
import com.paytmlabs.akka.cluster.discovery.Discovery

object ELBDiscovery extends ExtensionId[ELBDiscoveryExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Discovery.Extension] = ELBDiscovery
  override def createExtension(system: ExtendedActorSystem): ELBDiscoveryExtension = new ELBDiscoveryExtension(system)
}

class ELBDiscoveryExtension(override val system: ExtendedActorSystem) extends Discovery.Extension {
  val log = Logging(system, classOf[ELBDiscoveryExtension])
  log.info("ELBDiscoveryExtension created")

  override def start(): Boolean = {
    val discoveryConf = system.settings.config.getConfig("akka.cluster.discovery")
    if (discoveryConf.getBoolean("enabled")) {
      startSeedActor()
      system.actorOf(ELBActor.props, "ELBActor")
      true
    } else false
  }
}