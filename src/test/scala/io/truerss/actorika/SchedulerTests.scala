package io.truerss.actorika

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._

class SchedulerTests extends Test {

  @volatile private var onceCalled = false

  private class TestActor extends Actor {

    override def preStart(): Unit = {
      scheduler.once(1.seconds) { () =>
        onceCalled = true
      }
    }

    override def receive: Receive = {
      case _ =>
    }
  }

  test("scheduler") {
    val system = ActorSystem("test")
    system.spawn(new TestActor, "actor")
    Thread.sleep(1500)
    assert1(onceCalled)
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
    assert1(index.get() == 3)
    assert1(index1.get() == 4)
    assert1(ms(start, end) >= 3)
    assert1(flag)
    assert1(ms(start, end) >= 3)
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
    assert1(index.get() > 10)
    sch.stop()
  }

  test("scheduler ~ clear") {
    val sch = new Scheduler(ActorSystem.threadFactory("test"))
    val index = new AtomicInteger(0)
    val task = sch.every(100.millis) {() =>
      val tmp = index.incrementAndGet()
      if (tmp == 1) {
        throw new RuntimeException("boom")
      }
    }
    task.clear()
    assert1(index.get() == 0, s"index=${index.get()}")
    sch.stop()
  }

  private def ms(start: Long, end: Long): Long = {
    (end - start) / 1000 % 60
  }

}
