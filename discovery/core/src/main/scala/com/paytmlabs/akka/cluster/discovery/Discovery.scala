package com.paytmlabs.akka.cluster.discovery

import akka.actor.ExtendedActorSystem
import akka.cluster.Cluster

object Discovery {
  trait Extension extends akka.actor.Extension {
    def system: ExtendedActorSystem

    def start(): Boolean

    def startSeedActor(): Unit = {
      val conf = system.settings.config
      val discoveryConf = conf.getConfig("akka.cluster.discovery")

      val cluster = Cluster(system)
      system.actorOf(SeedActor.props)
    }
  }
}