package io.truerss.actorika

class ActorSystemStrategyResolveTests extends munit.FunSuite {

  import scala.jdk.CollectionConverters._

   /*
         system
       a1     b1
     /          \
   a2            b2

   */

  private object Spawn

  private trait Empty extends Actor {
    override def receive: Receive = {
      case _ =>
    }
  }

  private class A2 extends Empty { }
  private class A1 extends Actor {
    override def receive: Receive = {
      case Spawn =>
        spawn(new A2, "a2")
    }
  }
  private class B2 extends Empty { }
  private class B1 extends Actor {
    override def applyRestartStrategy(ex: Throwable, failedMessage: Option[Any], count: Int): ActorStrategies.Value = {
      ActorStrategies.Restart
    }

    override def receive: Receive = {
      case Spawn =>
        spawn(new B2, "b2")
    }
  }


  test("resolve actor strategies") {
    val system = ActorSystem("system")
    val a1 = system.spawn(new A1(), "a1")
    val b1 = system.spawn(new B1(), "b1")
    system.start()
    system.send(a1, Spawn)
    system.send(b1, Spawn)

    Thread.sleep(100)
    // check
    assertEquals(system.world.size(), 4)
    // check strategies
    val a2 = system.world.asScala.find(x => x._1.contains("a2")).get._2
    val a2s = system.resolveStrategy(a2.ref)
    assertEquals(a2s.size, 3)
    val ex = new Exception("boom")
    val a2resolved = a2s.map { x => x.apply(ex, None, 0) }

    assertEquals(a2resolved, Vector(
      ActorStrategies.Parent,
      ActorStrategies.Parent,
      ActorStrategies.Stop
    ))

    val b2 = system.world.asScala.find(x => x._1.contains("b2")).get._2
    val b2s = system.resolveStrategy(b2.ref)
    assertEquals(b2s.size, 3)
    val b2resolved = b2s.map { x => x.apply(ex, None, 0) }

    assertEquals(b2resolved, Vector(
      ActorStrategies.Parent,
      ActorStrategies.Restart,
      ActorStrategies.Stop
    ))
    system.stop()
  }

}
