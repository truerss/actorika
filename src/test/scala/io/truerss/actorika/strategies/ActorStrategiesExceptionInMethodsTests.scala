package io.truerss.actorika.strategies

import io.truerss.actorika._

class ActorStrategiesExceptionInMethodsTests extends CommonTest {

  import ActorStates._
  import scala.jdk.CollectionConverters._

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



}
