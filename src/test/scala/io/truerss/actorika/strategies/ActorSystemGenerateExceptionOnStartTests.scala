package io.truerss.actorika.strategies

import io.truerss.actorika.{Actor, ActorSystem, ActorSystemSettings}

class ActorSystemGenerateExceptionOnStartTests extends CommonTest {

  private class MyActor extends Actor with Empty {
    override def preStart(): Unit = {
      throw new RuntimeException("boom")
    }
  }

  test("should produce error") {
    try {
      val system = ActorSystem("test-system",
        ActorSystemSettings.default.copy(exceptionOnStart = true)
      )
      system.spawn(new MyActor)
      assert(false, "exception is required!")
    } catch {
      case ex: Throwable =>
        assert(ex.getMessage.contains("Failed to start actor:"))
    }
  }

  test("do not produce error") {
    try {
      val system = ActorSystem("test-system",
        ActorSystemSettings.default.copy(exceptionOnStart = false)
      )
      system.spawn(new MyActor)
      assert(true)
    } catch {
      case ex: Throwable =>
        assert(false, "oops, unexpected exception")
    }
  }


}
