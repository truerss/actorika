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
    assert(system.world.size == 2)
    system.send(fooRef, Start)
    system.send(fooRef, Start1)
    Thread.sleep(100)
    assert(system.world.size == 4)
    val baz = system.world.asScala.find(x => x._1.endsWith("baz")).head._2

    assert(system.findMe(baz.ref).get == baz)

    system.stop()
  }

}
