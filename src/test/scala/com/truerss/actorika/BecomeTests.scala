package com.truerss.actorika

import java.util.concurrent.{ConcurrentLinkedQueue => CLQ}
import java.util.concurrent.atomic.AtomicInteger

class BecomeTests extends Test {

  private case object A
  private case object B
  private case class Set(value: Int)
  private case object Boom

  @volatile private var flag = false
  @volatile private var restared = false
  private val unhandled = new CLQ[Any]()
  private val checker = new AtomicInteger(0)

  private class TestActor extends Actor {
    override def preRestart(): Unit = {
      restared = true
    }

    override def receive: Receive = {
      case A =>
        flag = true
        become(handleA)

      case Set(value) =>
        checker.set(value*2)
    }

    private def handleA: Receive = {
      case B =>
        flag = false
        become(receive)

      case Boom =>
        throw new Exception("boom")

      case Set(value) =>
        checker.set(value + 1)
    }

    override def onUnhandled(message: Any): Unit = {
      unhandled.add(message)
    }
  }

  test("become: switch actor state") {
    val setup = ActorSetup.default
      .copy(defaultStrategy = ActorStrategies.Restart)
      .copy(maxRestartCount = 3)
    val system = ActorSystem("system", setup)
    val ref = system.spawn(new TestActor, "test")
    system.tell(Set(3), ref)
    assert1(checker.get() == 6)
    system.tell(B, ref)
    assert1(unhandled.size() == 1)
    system.tell(A, ref)
    assert1(flag)
    assert1(checker.get() == 6)
    system.tell(A, ref)
    assert1(unhandled.size() == 2)
    system.tell(Set(9), ref)
    assert1(checker.get() == 10)
    assert1(flag)
    // switch again
    system.tell(B, ref)
    assert1(!flag)
    // should use original Receive after restart
    system.tell(A, ref)
    assert1(flag)
    system.tell(Set(42), ref)
    assert1(checker.get() == 43)
    system.tell(Boom, ref)
    assert1(checker.get() == 43)
    assert1(restared)
    system.tell(Set(42), ref)
    assert1(checker.get() == 84)
    assert1(unhandled.size() == 3) // Boom is unhandled after restart
  }
}
