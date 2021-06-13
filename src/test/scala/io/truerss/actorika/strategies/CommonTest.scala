package io.truerss.actorika.strategies

import io.truerss.actorika.{Actor, ActorStates, ActorStrategies}
import io.truerss.actorika.ActorStates.ActorState

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

trait CommonTest extends munit.FunSuite {

  val stopCounter = new AtomicInteger(0)
  val startCounter = new AtomicInteger(0)
  val restartCounter = new AtomicInteger(0)

  val receivedExceptions = new ConcurrentLinkedQueue[Throwable]()
  val receivedMessages = new ConcurrentLinkedQueue[Any]()

  val states = new ConcurrentLinkedQueue[ActorStates.ActorState]()

  trait CommonLT extends Actor {
    override protected[actorika] def moveStateTo(newState: ActorState): Unit = {
      states.add(newState)
      super.moveStateTo(newState)
    }
    override def preStart(): Unit = startCounter.incrementAndGet()

    override def postStop(): Unit = {
      stopCounter.incrementAndGet()
    }

    override def preRestart(): Unit = restartCounter.incrementAndGet()
  }

  trait Empty { self: Actor =>
    override def receive: Receive = {
      case _ =>
    }
  }

  trait RestartStrategyImpl { self: Actor =>
    override def applyRestartStrategy(ex: Throwable, failedMessage: Option[Any], count: Int): ActorStrategies.Value = {
      receivedExceptions.add(ex)
      failedMessage.foreach { x => receivedMessages.add(x) }
      if (count == 3) {
        ActorStrategies.Stop
      } else {
        ActorStrategies.Restart
      }
    }
  }

  def reset(): Unit = {
    stopCounter.set(0)
    startCounter.set(0)
    restartCounter.set(0)
    receivedExceptions.clear()
    receivedMessages.clear()
    states.clear()
  }

}
