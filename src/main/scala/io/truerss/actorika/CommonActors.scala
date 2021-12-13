package io.truerss.actorika

import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration

private [actorika] object CommonActors {
  def systemActor(setup: ActorSetup): Actor = new Actor {
    override def applyStrategy(throwable: Throwable, failedMessage: Option[Any], restartCount: Int): ActorStrategies.Value = {
      setup.defaultStrategy
    }

    override def receive: Receive = {
      case message =>
        onUnhandled(message)
    }

    override def onUnhandled(message: Any): Unit = {
      ActorSystem.logger.warn(s"Unhandled message: $message")
    }
  }

  def askActor(message: Any, timeout: FiniteDuration, promise: Promise[Any]): Actor = {
    new Actor {
      var task: SchedulerTask = null
      override def receive: Receive = {
        case StartAsk =>
          task = system.scheduler.once(timeout) { () =>
            me.tell(AskTimeout, me)
          }

        case AskTimeout =>
          task.clear()
          promise.failure(AskException(s"Timeout $timeout is over on $message message"))
          stop()
        case msg: Any =>
          task.clear()
          // race
          if (!promise.isCompleted) {
            promise.success(msg)
          }
          stop()
      }
    }
  }
}
