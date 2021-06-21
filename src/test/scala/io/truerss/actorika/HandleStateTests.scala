package io.truerss.actorika

import java.util.concurrent.{ConcurrentLinkedQueue => CLQ}

class HandleStateTests extends munit.FunSuite {

  private val xs = new CLQ[Int]()

  private class TestActor extends Actor {
    private var state: Int = 0
    def receive: Receive = {
      case x: Int =>
        if (x != state) {
          xs.add(x)
        }
        state = state + 1
    }
  }


  test("check state") {
    val system = ActorSystem("system")
    val ref = system.spawn(new TestActor, "test")
    val xs = 0 to 100000
    xs.foreach { x =>
      system.send(ref, x)
    }
    val ra = system.findMe(ref).get
    while (ref.hasMessages) {
      ra.tick()
    }
    Thread.sleep(100)
    assert(xs.nonEmpty)
  }

}
