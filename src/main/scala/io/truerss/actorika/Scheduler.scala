package io.truerss.actorika

import org.slf4j.LoggerFactory

import java.util.concurrent.{Executors, ThreadFactory, TimeUnit}
import scala.concurrent.duration.FiniteDuration

class SchedulerTask(f: () => Unit) {
  @volatile private var cleared = false
  def clear() = {
    cleared = true
  }

  def isClear: Boolean = cleared

  def call(): Unit = f.apply()
}

class Scheduler(tf: ThreadFactory) {

  private val executor = Executors.newSingleThreadScheduledExecutor(tf)
  private val logger = LoggerFactory.getLogger(getClass)

  def every(period: FiniteDuration)(f: () => Unit): SchedulerTask = {
    every(period, FiniteDuration(0, TimeUnit.MILLISECONDS))(f)
  }

  def every(period: FiniteDuration, delay: FiniteDuration)(f: () => Unit): SchedulerTask = {
    val tmp = new SchedulerTask(f)
    executor.scheduleAtFixedRate(run(tmp), delay.toMillis, period.toMillis, TimeUnit.MILLISECONDS)
    tmp
  }

  def once(delay: FiniteDuration)(f: () => Unit): SchedulerTask = {
    val tmp = new SchedulerTask(f)
    executor.schedule(run(tmp), delay.toMillis, TimeUnit.MILLISECONDS)
    tmp
  }

  private[actorika] def stop(): Unit = {
    executor.shutdown()
  }

  private def run(task: SchedulerTask): Runnable = {
    new Runnable {
      override def run(): Unit = {
        if (!task.isClear) {
          try {
            task.call()
          } catch {
            case ex: Throwable =>
              logger.warn(s"Failed to execute action: $task", ex)
          }
        }
      }
    }
  }

}
