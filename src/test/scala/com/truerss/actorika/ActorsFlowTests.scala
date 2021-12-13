package com.truerss.actorika
import java.util.concurrent.{ConcurrentLinkedQueue => CLQ}
import scala.jdk.CollectionConverters

class ActorsFlowTests extends Test {

  import CollectionConverters._

  private val q = new CLQ[Int]()
  private val u = new CLQ[String]()

  @volatile
  private var isStartCalled = false

  @volatile
  private var isStopCalled = false

  private class TestActor extends Actor {
    override def receive: Receive = {
      case x: Int =>
        q.add(x)
    }

    override def onUnhandled(message: Any): Unit = {
      u.add(message.toString)
    }

    override def preStart(): Unit = {
      isStartCalled = true
    }

    override def postStop(): Unit = {
      isStopCalled = true
    }

  }

  test("can run actor, and do not handle unexpected messages") {
    val system = ActorSystem("test-system")
    val actor = new TestActor
    val ref = system.spawn(actor, "test")
    val xs = 0 to 10
    xs.foreach { x =>
      ref.tell(x, ref)
      // unhandled
      ref.tell(s"$x", ref)
    }

    Thread.sleep(1000)

    assert(q.size() == xs.size, s"queue size = ${q.size()}, collection size = ${xs.size}")
    q.asScala.zip(xs).foreach { case (a, b) =>
      assert(a == b)
    }

    assert(u.size() == xs.size, s"queue size = ${u.size()}, collection size = ${xs.size}")
    u.asScala.zip(xs).foreach { case (a, b) =>
      assert(a == s"$b")
    }

    assert(actor._state == ActorStates.Live)

    system.stop()

    assert(actor._state == ActorStates.Finished, s"need Finished, but given: ${actor._state}")

    assert(isStartCalled, "preStart was not called")

    assert(isStopCalled, "postStop was not called")
  }

  test("Kill message") {
    q.clear()
    val system = ActorSystem("test-system")
    val actor = new TestActor
    val ref = system.spawn(actor, "test")

    system.tell(1, ref)

    assert1(q.size() == 1)

    system.tell(Kill, ref)

    assert1(actor._state == ActorStates.Finished, s"need Finished, but given: ${actor._state}")

    system.tell(10, ref)

    assert(isStartCalled, "preStart was not called")

    assert(isStopCalled, "postStop was not called")
  }
}
