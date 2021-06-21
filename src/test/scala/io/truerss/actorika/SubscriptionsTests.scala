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
    assertEquals(ref.associatedMailbox.size(), xs.size, s"ref.size=${ref.associatedMailbox.size()} and ${xs.size}")
    assertEquals(ref1.associatedMailbox.size(), 0, s"ref1.size=${ref1.associatedMailbox.size()}")
    assertEquals(ref3.associatedMailbox.size(), xs.size, s"ref3.size=${ref3.associatedMailbox.size()}")
    while (ref.hasMessages || ref3.hasMessages) {
      system.systemActor.children.forEach { (_, a) => a.tick() }
    }
    Thread.sleep(100)
    assertEquals(ref.associatedMailbox.size(), 0, s"ref.size=${ref.associatedMailbox.size()}")
    assertEquals(ref3.associatedMailbox.size(), 0, s"ref3.size=${ref3.associatedMailbox.size()}")
    assertEquals(counter.get(), xs.size*2)
    assertEquals(ref1.associatedMailbox.size(), 0, s"ref1.size=${ref1.associatedMailbox.size()}")
    // unsubscribe
    system.unsubscribe(ref, classOf[Message])
    val tmpRef = system.findMe(ref)
    assert(tmpRef.get.subscriptions.isEmpty, s"isEmpty?=${tmpRef.get.subscriptions.isEmpty}")
    val tmpRef3 = system.findMe(ref3)
    assert(!tmpRef3.get.subscriptions.isEmpty, s"!ref3.isEmpty=${tmpRef3.get.subscriptions.isEmpty}")
    system.subscribe(ref, classOf[Message])
    system.subscribe(ref, classOf[Int])
    val tmp1Ref = system.findMe(ref)
    assert(tmp1Ref.get.subscriptions.size == 2, s"ref.size=${tmp1Ref.get.subscriptions.size}")
    system.unsubscribe(ref, classOf[Int])
    system.unsubscribe(ref, classOf[String]) // not present, but ...
    val tmp2Ref = system.findMe(ref)
    assert(tmp2Ref.get.subscriptions.size == 1, s"ref.size=${tmp2Ref.get.subscriptions.size}")
    system.unsubscribe(ref)
    val tmp3Ref = system.findMe(ref)
    assert(tmp3Ref.get.subscriptions.size == 0, s"ref.size=${tmp3Ref.get.subscriptions.size}")
  }

}
