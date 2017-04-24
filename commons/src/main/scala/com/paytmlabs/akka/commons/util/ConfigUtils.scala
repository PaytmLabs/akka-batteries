package com.paytmlabs.akka.commons.util

import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import com.typesafe.config.Config

object ConfigUtils {

  implicit class RichConfig(val config: Config) extends AnyVal {

    def getFiniteDuration(path: String): FiniteDuration =
      FiniteDuration(config.getDuration(path, TimeUnit.NANOSECONDS), TimeUnit.NANOSECONDS)

    def firstLevelKeys: Set[String] = {
      import scala.collection.JavaConverters._
      config.entrySet().asScala.map(_.getKey.split('.').head).toSet
    }
  }

}
