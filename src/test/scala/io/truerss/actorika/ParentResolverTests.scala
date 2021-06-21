package io.truerss.actorika

class ParentResolverTests extends munit.FunSuite {

  import scala.jdk.CollectionConverters._

  private case object Start

  private case object Start1

  private class BazActor extends Actor {
    override def receive: Receive = {
      case _ =>
    }
  }

  private class BarActor extends Actor {
    override def receive: Receive = {
      case Start =>
        spawn(new BazActor, "baz")
    }
  }

  private class FooActor extends Actor {
    private var ref: ActorRef = null
    override def receive: Receive = {
      case Start =>
        ref = spawn(new BarActor, "bar")
      case Start1 =>
        me.send(ref, Start)
    }
  }

  private class TestActor extends Actor {
    override def receive: Receive = {
      case _ =>
    }
  }

  test("detect parent") {
    val system = ActorSystem("system")
    val testRef = system.spawn(new TestActor, "test")
    val fooRef = system.spawn(new FooActor, "foo")
    system.start()
    assert(system.size == 2)
    system.send(fooRef, Start)
    system.send(fooRef, Start1)
    Thread.sleep(100)
    assert(system.findMe(testRef).get.children.size == 0)
    assert(system.findMe(fooRef).get.children.size == 1)
    assert(system.findRealActor("bar").get.children.size == 1)
    assert(system.findRealActor("baz").get.actor._parent == system.findRealActor("bar").get.ref)

    system.stop()
  }

}
