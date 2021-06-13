package io.truerss.actorika

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class ActorStrategiesTests extends munit.FunSuite {

  import scala.jdk.CollectionConverters._
  import ActorStates._

  private val stopCounter = new AtomicInteger(0)
  private val startCounter = new AtomicInteger(0)
  private val restartCounter = new AtomicInteger(0)

  private val receivedExceptions = new ConcurrentLinkedQueue[Throwable]()
  private val receivedMessages = new ConcurrentLinkedQueue[Any]()

  private val states = new ConcurrentLinkedQueue[ActorStates.ActorState]()

  private def reset(): Unit = {
    stopCounter.set(0)
    startCounter.set(0)
    restartCounter.set(0)
    receivedExceptions.clear()
    receivedMessages.clear()
    states.clear()
  }

  private trait CommonLT extends Actor {
    override protected[actorika] def moveStateTo(newState: ActorState): Unit = {
      states.add(newState)
      super.moveStateTo(newState)
    }
    override def preStart(): Unit = startCounter.incrementAndGet()

    override def postStop(): Unit = stopCounter.incrementAndGet()

    override def preRestart(): Unit = restartCounter.incrementAndGet()
  }

  private trait Empty { self: Actor =>
    override def receive: Receive = {
      case _ =>
    }
  }

  private trait RestartStrategyImpl { self: Actor =>
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

  private class StopStrategy extends CommonLT {
    override def receive: Receive = {
      case _ =>
        throw new Exception("boom")
    }
  }

  private class RestartStrategy extends CommonLT with RestartStrategyImpl {
    override def receive: Receive = {
      case _ =>
        throw new Exception("boom")
    }
  }

  private class ExceptionInPreStartAndStopStrategy extends CommonLT with Empty {
    override def preStart(): Unit = {
      startCounter.incrementAndGet()
      throw new Exception("boom")
    }
  }

  private class ExceptionInPreStartAndRestartStrategy extends CommonLT with Empty {
    override def preStart(): Unit = {
      startCounter.incrementAndGet()
      throw new Exception("boom")
    }

    override def receive: Receive = {
      case _ =>
        throw new Exception("boom")
    }
  }

  private class IgnoreExceptionInPostStop extends CommonLT with Empty {
    override def postStop(): Unit = {
      stopCounter.incrementAndGet()
      throw new Exception("boom")
    }
  }

  private class FailedOnRestart extends CommonLT with Empty with RestartStrategyImpl {
    override def preRestart(): Unit = {
      restartCounter.incrementAndGet()
      throw new Exception("preRestart")
    }
    override def receive: Receive = {
      case _ =>
        throw new Exception("boom")
    }
  }

  private class SkipStrategy extends Actor {
    private var index = -1
    def receive: Receive = {
      case _ =>
        index = index + 1
        if (index == 1) {
          throw new Exception("boom")
        }
    }

    override def applyRestartStrategy(ex: Throwable, failedMessage: Option[Any], count: Int): ActorStrategies.Value = {
      receivedExceptions.add(ex)
      ActorStrategies.Skip
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
    assertEquals(states.asScala.toVector, Vector(Uninitialized, Live, Stopped))
  }

  test("restart strategy") {
    reset()
    val system = ActorSystem("system")
    system.start()
    val ref = system.spawn(new RestartStrategy, "test")

    def again(): Unit = {
      system.send(ref, "boom")
      Thread.sleep(100)
    }
    again()
    assertEquals(stopCounter.get(), 1)
    assertEquals(startCounter.get(), 2)
    assertEquals(restartCounter.get(), 1)
    assertEquals(
      states.asScala.toVector,
      Vector(
        Uninitialized, Live, // started
        Uninitialized, Stopped, // message passed
        Uninitialized, Live // restarted
      )
    )
    again()
    //TODO assertEquals(stopCounter.get(), 2)
//    assertEquals(restartCounter.get(), 2)
//    assertEquals(startCounter.get(), 3)
//    assertEquals(receivedMessages.size(), 2)
//    assertEquals(receivedExceptions.size(), 2)
    system.stop()
  }

  test("exception in preStart#Stop") {
    reset()
    val system = ActorSystem("system")
    val ref = system.spawn(new ExceptionInPreStartAndStopStrategy, "test")
    Thread.sleep(100)
    val ra = system.world.get(ref.path)
    assert(Option(ra).isEmpty)
    assertEquals(stopCounter.get(), 1)
    assertEquals(startCounter.get(), 1)
    assertEquals(restartCounter.get(), 0)
    assertEquals(
      states.asScala.toVector,
      Vector(Uninitialized, Stopped) // start and stop
    )
  }

  test("ignore exceptions in postStop") {
    reset()
    val system = ActorSystem("system")
    val ref = system.spawn(new IgnoreExceptionInPostStop, "test")
    val ra = system.world.get(ref.path)
    assertEquals(ra.actor._state, ActorStates.Live)
    // ok, stop the actor
    system.stop(ref)
    assertEquals(stopCounter.get(), 1)
    assertEquals(ra.actor._state, ActorStates.Stopped)
    assertEquals(
      states.asScala.toVector,
      Vector(
        Uninitialized, Live, Stopped
      )
    )
  }

  test("exception in preRestart#Restart") {
    reset()
    val system = ActorSystem("system")
    val ref = system.spawn(new FailedOnRestart, "test123")
    val ra = system.world.get(ref.path)
    assertEquals(ra.actor._state, ActorStates.Live)
    system.send(ref, "boom")
    while (ref.hasMessages) {
      ra.tick()
    }
    Thread.sleep(100)
    assertEquals(ra.actor._state, Stopped)
    assertEquals(restartCounter.get(), 3)
    assertEquals(receivedMessages.size(), 4)
    assertEquals(receivedExceptions.size(), 4)
    assertEquals(
      receivedExceptions.asScala.map(_.getMessage).toVector,
      Vector("boom", "preRestart", "preRestart", "preRestart")
    )
  }

  test("overlimitting in restarts") {
    reset()
    val system = ActorSystem("system", ActorSystemSettings(
      handleDeadLetters = false,
      maxRestartCount = 2,
      defaultExecutor = null
    ))
    val ref = system.spawn(new FailedOnRestart, "test123")
    val ra = system.world.get(ref.path)
    assertEquals(ra.actor._state, ActorStates.Live)
    system.send(ref, "boom")
    while (ref.hasMessages) {
      ra.tick()
    }
    Thread.sleep(100)
    assertEquals(ra.actor._state, Stopped)
    assertEquals(restartCounter.get(), 3)
    assertEquals(receivedMessages.size(), 3)
    assertEquals(receivedExceptions.size(), 3)
    assertEquals(
      receivedExceptions.asScala.map(_.getMessage).toVector,
      Vector("boom", "preRestart", "preRestart")
    )
  }

  test("skip strategy check") {
    reset()
    val system = ActorSystem("test")
    val ref = system.spawn(new SkipStrategy(), "actor")
    system.send(ref, "a") // index => 0
    system.send(ref, "b") // index => 1
    system.send(ref, "c") // index => 2
    system.send(ref, "d") // index => 3
    assertEquals(ref.associatedMailbox.size(), 4)
    val ra = system.world.get(ref.path)
    (0 to 2).foreach { _ => ra.tick() } // 0, 1
    Thread.sleep(100)
    assert(ref.associatedMailbox.size() > 0)
    while(ref.hasMessages) {
      ra.tick()
    }
    Thread.sleep(100)
    assert(ref.associatedMailbox.isEmpty)
    assert(receivedExceptions.size() == 1)
    assert(restartCounter.get == 0)
    assert(stopCounter.get == 0)
    assert(startCounter.get == 0)
  }



}
