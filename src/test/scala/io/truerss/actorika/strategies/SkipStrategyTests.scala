package io.truerss.actorika.strategies

import io.truerss.actorika.{Actor, ActorStrategies, ActorSystem}

class SkipStrategyTests extends CommonTest {

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

  test("skip strategy check") {
    reset()
    val system = ActorSystem("test")
    val ref = system.spawn(new SkipStrategy(), "actor")
    system.send(ref, "a") // index => 0
    system.send(ref, "b") // index => 1
    system.send(ref, "c") // index => 2
    system.send(ref, "d") // index => 3
    assertEquals(ref.associatedMailbox.size(), 4)
    val ra = system.findRealActor(ref.path).get
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
