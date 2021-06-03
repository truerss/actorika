package io.truerss.actorika

class ActorSystemTests extends munit.FunSuite {

  import scala.jdk.CollectionConverters._

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
    var stopped = false
    val system = ActorSystem("system")
    system.registerOnTermination(() => stopped = true)
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
    assert(stopped)
  }

  test("automatic actor name allocator") {
    val system = ActorSystem("test")
    val xs = 0 to 2
    xs.foreach { _ => system.spawn(new TestActor) }
    assertEquals(system.world.size(), xs.size)
    system.world.asScala.foreach { x =>
      assert(x._1.contains("actor-"))
    }
  }

}
