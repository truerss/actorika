package io.truerss.actorika

class ActorSystemTests extends munit.FunSuite {

  import scala.jdk.CollectionConverters._

  @volatile var flag = false

  private class TestActor extends Actor {

    override def postStop(): Unit = {
      flag = true
    }

    def receive: Receive = {
      case _ =>
    }
  }

  test("start/stop system") {
    var stopped = false
    val system = ActorSystem("system")
    system.registerOnTermination(() => stopped = true)
    system.spawn(new TestActor, "test")
    system.start()
    Thread.sleep(100)
    assertEquals(system.systemActor.children.size(), 1)
    assert(!system.stopSystem)
    // stop then
    system.stop()
    Thread.sleep(100)
    assert(system.stopSystem)
    assert(system.isEmpty)
    assert(flag)
    assert(stopped)
  }

  test("automatic actor name allocator") {
    val system = ActorSystem("test")
    val xs = 0 to 2
    xs.foreach { _ => system.spawn(new TestActor) }
    Thread.sleep(100)
    assertEquals(system.systemActor.children.size, xs.size)
    system.systemActor.children.asScala.foreach { x =>
      assert(x._1.contains("actor-"))
    }
  }

  test("failed to start actor") {
    try {
      val system = ActorSystem("test")
      system.spawn(new TestActor, "@asd)")
      assert(false, "oops")
    } catch {
      case ex: Throwable =>
        assert(ex.getMessage.contains("Invalid actor name:"))
    }
  }

  test("findParent#None") {
    val system = ActorSystem("test")
    assert(system.findParent(ActorRef(Address("asd"))).isEmpty)
  }

  test("exception when parent is not exist") {
    val system = ActorSystem("test")
    try {
      system.allocate(new TestActor, "asd", ActorRef(Address("abc")))
      assert(false)
    } catch {
      case ex: Throwable =>
        assert(ex.getMessage.contains(s"Actor#abc is not exist"))
    }
  }

}
