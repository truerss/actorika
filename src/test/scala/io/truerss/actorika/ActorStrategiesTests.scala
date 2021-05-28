package io.truerss.actorika

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class ActorStrategiesTests extends munit.FunSuite {

  private val stopCounter = new AtomicInteger(0)
  private val startCounter = new AtomicInteger(0)
  private val restartCounter = new AtomicInteger(0)

  private val receivedExceptions = new ConcurrentLinkedQueue[Throwable]()
  private val receivedMessages = new ConcurrentLinkedQueue[Any]()

  private def reset(): Unit = {
    stopCounter.set(0)
    startCounter.set(0)
    restartCounter.set(0)
    receivedExceptions.clear()
    receivedMessages.clear()
  }

  private trait CommonLT extends Actor {
    override def preStart(): Unit = startCounter.incrementAndGet()

    override def postStop(): Unit = stopCounter.incrementAndGet()

    override def preRestart(): Unit = restartCounter.incrementAndGet()
  }

  private class StopStrategy extends CommonLT {
    override def receive: Receive = {
      case _ =>
        throw new Exception("boom")
    }
  }

  private class RestartStrategy extends CommonLT {
    override def applyRestartStrategy(ex: Throwable, failedMessage: Option[Any], count: Int): ActorStrategies.Value = {
      receivedExceptions.add(ex)
      failedMessage.foreach { x => receivedMessages.add(x) }
      if (count > 3) {
        ActorStrategies.Stop
      } else {
        ActorStrategies.Restart
      }
    }

    override def receive: Receive = {
      case _ =>
        throw new Exception("boom")
    }
  }

  private class FailedOnRestart extends CommonLT {
    override def receive: Receive = {
      case _ =>
    }
  }

  test("check stop strategy") {
    reset()
    val system = ActorSystem("system")
    val ref = system.spawn(new StopStrategy, "test")
    system.send(ref, "boom")
    while (ref.hasMessages) {
      system.world.forEach((_, x) => x.tick())
    }
    Thread.sleep(100)
    assertEquals(system.world.size(), 0)
    assertEquals(stopCounter.get(), 1)
    assertEquals(startCounter.get(), 1)
    assertEquals(restartCounter.get(), 0)
  }

  test("restart strategy") {
    reset()
    val system = ActorSystem("system")
    val ref = system.spawn(new RestartStrategy, "test")

    def again(): Unit = {
      system.send(ref, "boom")
      while (ref.hasMessages) {
        system.world.forEach((_, x) => x.tick())
      }
      Thread.sleep(100)
    }
    again()
    assertEquals(system.world.size(), 1)
    assertEquals(stopCounter.get(), 1)
    assertEquals(startCounter.get(), 2)
    assertEquals(restartCounter.get(), 1)
    again()
    again()
    again()
    assertEquals(stopCounter.get(), 4)
    assertEquals(restartCounter.get(), 4)
    assertEquals(startCounter.get(), 5)
    assertEquals(receivedMessages.size(), 4)
    assertEquals(receivedExceptions.size(), 4)
  }



}
