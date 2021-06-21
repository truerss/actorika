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
    assertEquals(stopCounter.get(), 1, s"stop counter: ${stopCounter.get()}")
    assertEquals(startCounter.get(), 2, s"start counter: ${startCounter.get()}")
    assertEquals(restartCounter.get(), 1, s"restart counter: ${restartCounter.get()}")
    assertEquals(
      states.asScala.toVector,
      Vector(
        Uninitialized, Live, // started
        Uninitialized, Stopped, // message passed
        Uninitialized, Live // restarted
      )
    )
    again()
    assert(system.systemActor.children.size == 1, s"real=${system.systemActor.children.size}")
    assertEquals(stopCounter.get(), 2, s"stop counter: ${stopCounter.get()}")
    assertEquals(restartCounter.get(), 2, s"restart counter: ${restartCounter.get()}")
    assertEquals(startCounter.get(), 3, s"start counter: ${startCounter.get()}")
    assertEquals(receivedMessages.size(), 2, s"receivedMessages=${receivedMessages.size()}")
    assertEquals(receivedExceptions.size(), 2, s"receivedExceptions=${receivedExceptions.size()}")
    system.stop()
  }


}
