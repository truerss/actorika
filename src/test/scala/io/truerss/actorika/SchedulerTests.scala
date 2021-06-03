package io.truerss.actorika

import scala.concurrent.duration._

class SchedulerTests extends munit.FunSuite {

  @volatile private var onceCalled = false

  private class TestActor extends Actor {
    scheduler.once(3.seconds){ () =>
      onceCalled = true
    }
    def receive = {
      case _ =>
    }
  }

  test("scheduler".ignore) {
    val system = ActorSystem("test")
    val ref = system.spawn(new TestActor, "actor")
    Thread.sleep(4000)
    assert(onceCalled)
  }

}
