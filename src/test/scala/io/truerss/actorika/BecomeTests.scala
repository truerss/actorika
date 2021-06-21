package io.truerss.actorika

import java.util.concurrent.atomic.AtomicInteger

class BecomeTests extends munit.FunSuite {

  private case object A
  private case object B
  private case class Set(value: Int)
  private case object Boom

  @volatile private var flag = false
  @volatile private var restared = false
  private val checker = new AtomicInteger(0)

  private class TestActor extends Actor {

    override def applyRestartStrategy(ex: Throwable, failedMessage: Option[Any], count: Int): ActorStrategies.Value = {
      ActorStrategies.Restart
    }

    override def preRestart(): Unit = {
      restared = true
    }

    override def receive: Receive = {
      case A =>
        flag = true
        become(handleA)

      case Set(value) => checker.set(value*2)
    }

    private def handleA: Receive = {
      case B =>
        flag = false
        become(receive)

      case Boom =>
        throw new Exception("boom")

      case Set(value) => checker.set(value+1)
    }

  }

  test("become: switch actor state") {
    val system = ActorSystem("system")
    val ref = system.spawn(new TestActor, "test")
    val ra = system.findMe(ref).get
    system.send(ref, Set(3))
    tick(ra)
    assert(checker.get() == 6)
    system.send(ref, A)
    tick(ra)
    assert(flag)
    assert(checker.get() == 6)
    system.send(ref, Set(9))
    tick(ra)
    assert(checker.get() == 10)
    assert(flag)
    // switch again
    system.send(ref, B)
    tick(ra)
    assert(!flag)

    // should use original Receive after restart
    system.send(ref, A)
    tick(ra)
    assert(flag)
    system.send(ref, Set(42))
    tick(ra)
    assert(checker.get() == 43)
    system.send(ref, Boom)
    tick(ra)
    assert(checker.get() == 43)
    assert(restared)
    system.send(ref, Set(42))
    tick(ra)
    system.send(ref, Set(84))
  }



  private def tick(ra: RealActor): Unit = {
    ra.tick()
    Thread.sleep(100)
  }

}
