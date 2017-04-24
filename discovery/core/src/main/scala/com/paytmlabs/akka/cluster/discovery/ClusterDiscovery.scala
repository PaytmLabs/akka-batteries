package com.paytmlabs.akka.cluster.discovery

import com.paytmlabs.akka.cluster.discovery.network.NetworkDiscovery

import akka.actor.ExtendedActorSystem
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.event.Logging
import com.paytmlabs.akka.commons.util.ConfigUtils.RichConfig

object ClusterDiscovery extends ExtensionId[ClusterDiscovery] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Discovery.Extension] = ClusterDiscovery
  override def createExtension(system: ExtendedActorSystem): ClusterDiscovery = new ClusterDiscovery(system)
}

class ClusterDiscovery(override val system: ExtendedActorSystem) extends Discovery.Extension {
  val log = Logging(system, classOf[ClusterDiscovery])

  override def start(): Boolean = {

    val discoveryConf = system.settings.config.getConfig("akka.cluster.discovery")
    if (discoveryConf.getBoolean("enabled")) {
      val provider = discoveryConf.getString("provider").toLowerCase
      lazy val providerClass = discoveryConf.getString(s"modules.$provider.extension-class")
      lazy val validModules = discoveryConf.getConfig("modules").firstLevelKeys

      log.info("ClusterDiscovery provider: {}", provider)
      provider match {
        case "self" =>
          startSeedActor()
          system.actorOf(SelfJoinActor.props, "SelfJoinActor")

        case provider if validModules.contains(provider) =>
          log.info("Loading extension for provider: {}, with class: {}", provider, providerClass)
          val extentionLoader = system.dynamicAccess.getObjectFor[ExtensionId[Discovery.Extension]](providerClass)
          if (extentionLoader.isFailure) {
            bye(s"FATAL: Failed loading extension $providerClass with error extentionLoader. Ensure that you have the appropriate dependency")
          }
          extentionLoader.map { extension =>
            log.info("Loading extension: {}", extension)
            extension.get(system).start()
          }

        case unknown =>
          bye(s"FATAL: Unknown ClusterDiscovery provider: $unknown")
      }
      true
    } else false
  }

  def bye(msg: String = ""): Unit = {
    println(msg)
    System.exit(1)
  }
}