package io.truerss.actorika

import org.slf4j.LoggerFactory

import java.util.concurrent.{Executors, ThreadFactory, TimeUnit}
import scala.concurrent.duration.FiniteDuration

class Scheduler(tf: ThreadFactory) {

  private val executor = Executors.newSingleThreadScheduledExecutor(tf)
  private val logger = LoggerFactory.getLogger(getClass)

  def every(period: FiniteDuration)(f: () => Unit): Unit = {
    every(period, FiniteDuration(0, TimeUnit.MILLISECONDS))(f)
  }

  def every(period: FiniteDuration, delay: FiniteDuration)(f: () => Unit): Unit = {
    executor.scheduleAtFixedRate(run(f), delay.toMillis, period.toMillis, TimeUnit.MILLISECONDS)
  }

  def once(delay: FiniteDuration)(f: () => Unit): Unit = {
    executor.schedule(run(f), delay.toMillis, TimeUnit.MILLISECONDS)
  }

  private[actorika] def stop(): Unit = {
    executor.shutdown()
  }

  private def run(f: () => Unit): Runnable = {
    new Runnable {
      override def run(): Unit = {
        try {
          f.apply()
        } catch {
          case ex: Throwable =>
            logger.warn(s"Failed to execute action: $f", ex)
        }
      }
    }
  }

}
