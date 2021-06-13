package io.truerss.actorika.strategies

import io.truerss.actorika.ActorStates.{Live, Stopped, Uninitialized}
import io.truerss.actorika.{Actor, ActorStrategies, ActorSystem}

import scala.jdk.CollectionConverters._

class RestartStrategyTests extends CommonTest {



  private class RestartStrategy extends CommonLT with RestartStrategyImpl {
    override def receive: Receive = {
      case _ =>
        println("^"*100)
        throw new Exception("boom")
    }
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
    assert(system.world.size() == 1)
    assertEquals(stopCounter.get(), 2)
    assertEquals(restartCounter.get(), 2)
    assertEquals(startCounter.get(), 3)
    assertEquals(receivedMessages.size(), 2)
    assertEquals(receivedExceptions.size(), 2)
    system.stop()
  }


}
