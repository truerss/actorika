package io.truerss.actorika

class ActorSystemTests extends munit.FunSuite {

  @volatile var flag = false

  private class TestActor extends Actor {

    override def postStop(): Unit = {
      flag = true
    }

    def receive = {
      case _ =>
    }
  }

  test("start/stop system") {
    val system = ActorSystem("system")
    val ref = system.spawn(new TestActor, "test")
    system.start()
    Thread.sleep(100)
    assertEquals(system.world.size(), 1)
    assert(!system.stopSystem)
    // stop then
    system.stop()
    Thread.sleep(100)
    assert(system.stopSystem)
    assert(system.world.isEmpty)
    assert(flag)
  }

}
