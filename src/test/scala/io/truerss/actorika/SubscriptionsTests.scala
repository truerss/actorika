package io.truerss.actorika

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ConcurrentLinkedQueue => CLQ}

class SubscriptionsTests extends munit.FunSuite {

  private case class Message(id: Int)

  private val counter = new AtomicInteger(0)
  private val unhandled = new CLQ[Any]()

  private class TestActor extends Actor {
    def receive: Receive = {
      case Message(_) =>
        counter.incrementAndGet()
    }

    override def onUnhandled(msg: Any): Unit = {
      unhandled.add(msg)
    }
  }

  test("subscribe to messages") {
    val system = ActorSystem("test-system")
    val ref = system.spawn(new TestActor, "actor")
    val ref1 = system.spawn(new TestActor, "actor1")
    system.subscribe(ref, classOf[Message])
    val xs = 0 to 10
    xs.foreach { x =>
      system.publish(Message(x))
    }
    Thread.sleep(100)
    assertEquals(ref.associatedMailbox.size(), xs.size)
    assertEquals(ref1.associatedMailbox.size(), 0)
    while (!ref.associatedMailbox.isEmpty) {
      system.world.forEach { (_, a) => a.tick() }
    }
    Thread.sleep(100)
    assertEquals(ref.associatedMailbox.size(), 0)
    assertEquals(counter.get(), xs.size)
    assertEquals(ref1.associatedMailbox.size(), 0)
  }

}
