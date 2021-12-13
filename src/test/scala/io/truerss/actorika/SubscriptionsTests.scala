package io.truerss.actorika

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ConcurrentLinkedQueue => CLQ, ConcurrentHashMap => CHM}

class SubscriptionsTests extends Test {

  private case class Message(id: Int)

  sealed trait BaseMessage
  case class Message1(id: Int) extends BaseMessage
  case class Message2(id: String) extends BaseMessage

  private val map = new CHM[Int, Int]()
  map.put(0, 0)
  map.put(1, 0)
  map.put(2, 0)
  private val counter = new AtomicInteger(0)
  private val unhandled = new CLQ[Any]()

  private class TestActor(index: Int) extends Actor {
    def receive: Receive = {
      case Message(z) =>
        map.computeIfPresent(index, (_, v) => v + 1)
        counter.incrementAndGet()

      case _: BaseMessage =>
        counter.incrementAndGet()
    }

    override def onUnhandled(msg: Any): Unit = {
      unhandled.add(msg)
    }
  }

  test("subscribe/unsubscribe to messages") {
    val system = ActorSystem("test-system")
    val deadLettersCount = new AtomicInteger(0)
    system.registerDeadLetterHandler(new DeadLettersHandler {
      override def handle(message: Any, to: ActorRef, from: ActorRef): Unit = {
        deadLettersCount.incrementAndGet()
      }
    })
    val ref = system.spawn(new TestActor(0), "actor")
    val ref1 = system.spawn(new TestActor(1), "actor1")
    val ref2 = system.spawn(new TestActor(2), "actor3")
    system.subscribe(ref, classOf[Message])
    system.subscribe(ref2, classOf[Message])
    val xs = 0 to 10
    xs.foreach { x =>
      system.publish(Message(x))
    }

    assert1(map.get(0) == xs.size, s"map/0 = ${map.get(0)}, actual=${xs.size}")
    assert1(map.get(1) == 0, s"not subscribed, but message given")
    assert1(map.get(2) == xs.size)
    assert1(counter.get() == xs.size * 2)
    system.publish("123") // no subscribers

    // unsubscribe
    system.unsubscribe(ref, classOf[Message])
    val tmpRef = system.findMe(ref)
    assert1(tmpRef.get.subscriptions.isEmpty)
    val tmpRef3 = system.findMe(ref2)
    assert1(!tmpRef3.get.subscriptions.isEmpty)
    system.subscribe(ref, classOf[Message])
    system.subscribe(ref, classOf[Int])
    val tmp1Ref = system.findMe(ref)
    assert1(tmp1Ref.get.subscriptions.size == 2)
    system.unsubscribe(ref, classOf[Int])
    system.unsubscribe(ref, classOf[String]) // not present, but ...
    val tmp2Ref = system.findMe(ref)
    assert1(tmp2Ref.get.subscriptions.size == 1)
    system.unsubscribe(ref)
    val tmp3Ref = system.findMe(ref)
    assert1(tmp3Ref.get.subscriptions.size == 0)
    system.stop()
    assert1(deadLettersCount.get() == 1)
  }

//  test("handle subtypes") {
//    counter.set(0)
//    val system = ActorSystem("test")
//    system.start()
//    val ref = system.spawn(new TestActor(4), "asd")
//    system.subscribe(ref, classOf[BaseMessage])
//    system.publish(Message1(123))
//    system.publish(Message2("123"))
//    assert1(counter.get() == 2, s"counter=${counter.get()}")
//    system.stop()
//  }


}
