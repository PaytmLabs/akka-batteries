package com.paytmlabs.akka.cluster.sbr

import akka.actor.ActorSystem
import akka.cluster.DowningProvider
import akka.actor.Props
import scala.concurrent.duration.FiniteDuration
import scala.collection.JavaConverters._
import java.util.concurrent.TimeUnit
import com.paytmlabs.akka.commons.util.ConfigUtils.RichConfig

final class RoleBasedSplitBrainResolverProvider(system: ActorSystem) extends DowningProvider {

  val conf = system.settings.config
  private val stableAfter = conf.getFiniteDuration("app.cluster.stable-after")
  private val coreRoles = conf.getStringList("app.cluster.essential-roles").asScala.toSet

  override def downRemovalMargin: FiniteDuration = stableAfter

  override def downingActorProps: Option[Props] = Some(RoleBasedSplitBrainResolver.props(stableAfter, coreRoles))
}