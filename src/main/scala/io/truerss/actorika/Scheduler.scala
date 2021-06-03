package io.truerss.actorika

import org.slf4j.LoggerFactory

import java.util.concurrent.{Executors, ThreadFactory, TimeUnit}
import scala.concurrent.duration.FiniteDuration

class Scheduler(tf: ThreadFactory) {

  private val executor = Executors.newSingleThreadScheduledExecutor(tf)
  private val logger = LoggerFactory.getLogger(getClass)

  def every(period: FiniteDuration, f: () => Unit): Unit = {
    every(period, 0, f)
  }

  def every(period: FiniteDuration, delayMs: Long, f: () => Unit): Unit = {
    executor.scheduleAtFixedRate(run(f), delayMs, period.toMillis, TimeUnit.MILLISECONDS)
  }

  def once(delay: FiniteDuration)(f: () => Unit): Unit = {
    executor.schedule(run(f), delay.toMillis, TimeUnit.MILLISECONDS)
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
