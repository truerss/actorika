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

  test("subscribe/unsubscribe to messages") {
    val system = ActorSystem("test-system")
    val ref = system.spawn(new TestActor, "actor")
    val ref1 = system.spawn(new TestActor, "actor1")
    val ref3 = system.spawn(new TestActor, "actor3")
    system.subscribe(ref, classOf[Message])
    system.subscribe(ref3, classOf[Message])
    val xs = 0 to 10
    xs.foreach { x =>
      system.publish(Message(x))
    }
    Thread.sleep(100)
    assertEquals(ref.associatedMailbox.size(), xs.size)
    assertEquals(ref1.associatedMailbox.size(), 0)
    assertEquals(ref3.associatedMailbox.size(), xs.size)
    while (ref.hasMessages || ref3.hasMessages) {
      system.world.forEach { (_, a) => a.tick() }
    }
    Thread.sleep(100)
    assertEquals(ref.associatedMailbox.size(), 0)
    assertEquals(ref3.associatedMailbox.size(), 0)
    assertEquals(counter.get(), xs.size*2)
    assertEquals(ref1.associatedMailbox.size(), 0)
    // unsubscribe
    system.unsubscribe(ref, classOf[Message])
    assert(system.world.get(ref.path).subscriptions.isEmpty)
    assert(!system.world.get(ref3.path).subscriptions.isEmpty)
    system.subscribe(ref, classOf[Message])
    system.subscribe(ref, classOf[Int])
    assert(system.world.get(ref.path).subscriptions.size == 2)
    system.unsubscribe(ref, classOf[Int])
    system.unsubscribe(ref, classOf[String]) // not present, but ...
    assert(system.world.get(ref.path).subscriptions.size == 1)
  }

}
