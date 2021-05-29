package io.truerss.actorika

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ConcurrentLinkedQueue => CLQ}

class ActorTests extends munit.FunSuite {

  private val mailbox = new CLQ[Any]()
  private val preStartCalled = new AtomicInteger(0)
  private val postStopCalled = new AtomicInteger(0)
  private val preRestartCalled = new AtomicInteger(0)

  def reset(): Unit = {
    preStartCalled.set(0)
    postStopCalled.set(0)
    preRestartCalled.set(0)
  }

  case object GetSystem
  @volatile private var _currentSystem: ActorSystem = null
  @volatile private var _currentSender: ActorRef = null
  @volatile private var _currentThreadName: String = ""
  @volatile private var _fooParent: ActorRef = null
  case object Stop
  case object Allocate
  case object CheckParent
  case object StopChild

  private class TestActor extends Actor {

    private var _childRef: ActorRef = null

    override def preStart(): Unit = {
      preStartCalled.incrementAndGet()
    }

    override def postStop(): Unit = {
      postStopCalled.incrementAndGet()
    }

    override def preRestart(): Unit = {
      preRestartCalled.incrementAndGet()
    }

    override def receive: Receive = {
      case Stop =>
        stop()
      case Allocate =>
        _childRef = spawn(new FooActor, "foo")
      case CheckParent =>
        import Actor._
        _childRef ! CheckParent
      case StopChild =>
        stop(_childRef)
        _childRef = null

      case GetSystem =>
        _currentSystem = system
        _currentSender = sender
        _currentThreadName = Thread.currentThread().getName
      case msg =>

    }
  }

  private class FooActor extends Actor {
    import Actor._
    override def receive: Receive = {
      case CheckParent =>
        sender ! parent()
        _fooParent = parent()

      case _ =>
    }
  }

  private class CheckUnhandledActor extends Actor {
    override def receive: Receive = {
      case Stop =>
    }

    override def onUnhandled(msg: Any): Unit = {
      mailbox.add(msg)
    }
  }

  test("check lifecycle") {
    val system = ActorSystem("test-system")
    val ref = system.spawn(new TestActor, "actor")
    system.send(ref, "message")
    // ok, actor was registered
    assertEquals(preStartCalled.get(), 1)
    assertEquals(system.world.size(), 1)
    assertEquals(ref.associatedMailbox.size(), 1)
    val ra = system.world.get(ref.path)
    assertEquals(ra.actor.state, ActorStates.Live)
    // try to restart
    system.restart(ref)

    assertEquals(system.world.size(), 1)
    assertEquals(preStartCalled.get(), 2)
    assertEquals(preRestartCalled.get(), 1)
    assertEquals(postStopCalled.get(), 1)
    assertEquals(ref.associatedMailbox.size(), 0)
    // and stop
    system.stop(ref)
    assertEquals(ref.associatedMailbox.size(), 0)
    assertEquals(system.world.size(), 0)
    assertEquals(preStartCalled.get(), 2)
    assertEquals(preRestartCalled.get(), 1)
    assertEquals(postStopCalled.get(), 2)
    assertEquals(ra.actor.state, ActorStates.Stopped)
  }

  test("address must be unique") {
    val system = ActorSystem("test-system")
    system.spawn(new TestActor, "actor")
    try {
      system.spawn(new FooActor, "actor")
      assert(cond = false)
    } catch {
      case ex: Throwable =>
        assert(ex.getMessage.contains("Actor#actor already present"))
        assert(cond = true)
    }
    system.spawn(new FooActor, "foo")
    assertEquals(system.world.size(), 2)
  }

  test("can be stopped programmatically") {
    reset()
    val system = ActorSystem("test-system")
    val ref = system.spawn(new TestActor, "actor")
    system.send(ref, Stop)
    system.send(ref, "asd")
    val ra = system.world.get(ref.path)
    ra.tick()
    // sync time +-
    Thread.sleep(100)
    assertEquals(system.world.size(), 0)
    assertEquals(ref.associatedMailbox.size(), 0)
    assertEquals(postStopCalled.get(), 1)
    assertEquals(preRestartCalled.get(), 0)
    assertEquals(ra.actor.state, ActorStates.Stopped)
  }

  test("check system and context") {
    reset()
    val system = ActorSystem("test-system")
    val ref = system.spawn(new TestActor, "actor")
    system.send(ref, GetSystem)
    system.world.get(ref.path).tick()
    Thread.sleep(100)
    assertEquals(_currentSystem, system)
    assert(_currentSender.isSystemRef)
    assertEquals(_currentSender.path, system.systemName)
    assert(_currentThreadName.startsWith(s"${system.address.name}-pool-"))
  }

  test("create sub-actors") {
    import scala.jdk.CollectionConverters._
    reset()
    val system = ActorSystem("test-system")
    val ref = system.spawn(new TestActor, "actor")
    system.send(ref, Allocate)
    system.world.get(ref.path).tick()
    Thread.sleep(100)
    assertEquals(system.world.size, 2)
    // check parent
    system.send(ref, CheckParent)
    system.world.get(ref.path).tick()
    val ch = system.world.get(s"${ref.path}/foo")
    Thread.sleep(100)
    assertEquals(ch.ref.associatedMailbox.size(), 1)
    ch.tick()
    Thread.sleep(100)
    assertEquals(_fooParent.path, ref.path)
    assertEquals(_fooParent, ref)
    assertEquals(ref.associatedMailbox.size(), 1) // from fooActor one message
    // stop
    system.send(ref, StopChild)
    while (!ref.associatedMailbox.isEmpty) {
      system.world.get(ref.path).tick()
    }
    Thread.sleep(100)
    assertEquals(system.world.size(), 1)
    assertEquals(system.world.keys().asScala.toVector, Vector(ref.path))
  }

  test("unhandled check [deadletters]") {
    import scala.jdk.CollectionConverters._
    val system = ActorSystem("test-system")
    val ref = system.spawn(new CheckUnhandledActor, "actor")
    val xs = (0 to 10)
    xs.foreach { x =>
      system.send(ref, x)
    }
    while (!ref.associatedMailbox.isEmpty) {
      system.world.get(ref.path).tick()
    }
    Thread.sleep(100)
    assertEquals(mailbox.size(), xs.size)
    assertEquals(mailbox.asScala.toVector, xs.toVector)
  }

  test("do not process messages no in appropriate state") {
    val system = ActorSystem("test-system")
    val ref = system.spawn(new FooActor, "test")
    val xs = (0 to 10)
    xs.foreach { x =>
      system.send(ref, x)
    }
    val ra = system.world.get(ref.path)
    // actor is ready
    assertEquals(ra.actor.state, ActorStates.Live)
    assertEquals(ref.associatedMailbox.size, xs.size)
    ra.moveStateTo(ActorStates.Uninitialized)
    assertEquals(ra.actor.state, ActorStates.Uninitialized)
    (0 to 10).foreach { _ =>
      ra.tick()
    }
    Thread.sleep(100)
    assertEquals(ref.associatedMailbox.size, xs.size)
  }

}
