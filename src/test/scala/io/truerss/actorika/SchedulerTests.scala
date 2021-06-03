package io.truerss.actorika

import java.util.concurrent.atomic.AtomicInteger
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

  test("scheduler.every") {
    val sch = new Scheduler(ActorSystem.threadFactory("test"))
    val index = new AtomicInteger(0)
    val index1 = new AtomicInteger(0)
    @volatile var flag = false
    @volatile var end = 0L
    val start = System.currentTimeMillis()
    sch.once(3.seconds) { () =>
      flag = true
      end = System.currentTimeMillis()
    }
    sch.every(1.seconds, 1.second) { () =>
      index.incrementAndGet()
      end = System.currentTimeMillis()
    }
    sch.every(1.seconds) { () =>
      index1.incrementAndGet()
    }
    Thread.sleep(3500)
    // check
    assert(index.get() == 3)
    assert(index1.get() == 4)
    assert(ms(start, end) >= 3)
    assert(flag)
    assert(ms(start, end) >= 3)
    sch.stop()
  }

  test("scheduler should continue if exception was raised") {
    val sch = new Scheduler(ActorSystem.threadFactory("test"))
    val index = new AtomicInteger(0)
    sch.every(100.millis) {() =>
      val tmp = index.incrementAndGet()
      if (tmp == 1) {
        throw new RuntimeException("boom")
      }
    }
    Thread.sleep(1000)
    assert(index.get() > 10)
    sch.stop()
  }

  private def ms(start: Long, end: Long): Long = {
    (end - start) / 1000 % 60
  }

}
