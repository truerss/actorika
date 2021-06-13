package io.truerss.actorika.strategies

import io.truerss.actorika.ActorStates.{Live, Stopped, Uninitialized}
import io.truerss.actorika.{ActorStrategies, ActorSystem}

import scala.jdk.CollectionConverters._

class StopStrategyTests extends CommonTest {

  private class StopStrategy extends CommonLT {

    override def applyRestartStrategy(ex: Throwable, failedMessage: Option[Any], count: Int): ActorStrategies.Value = {
      ActorStrategies.Stop
    }

    override def receive: Receive = {
      case _ =>
        throw new Exception("boom")
    }
  }

  test("check stop strategy") {
    reset()
    val system = ActorSystem("system")
    system.start()
    val ref = system.spawn(new StopStrategy, "test")
    system.send(ref, "boom")
    Thread.sleep(100)
    assertEquals(system.world.size(), 0)
    assertEquals(stopCounter.get(), 1)
    assertEquals(startCounter.get(), 1)
    assertEquals(restartCounter.get(), 0)
    assertEquals(states.asScala.toVector, Vector(Uninitialized, Live, Stopped))
    system.stop()
  }

}
