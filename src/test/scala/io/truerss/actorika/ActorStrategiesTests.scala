package io.truerss.actorika

import java.util.concurrent.atomic.AtomicInteger

class ActorStrategiesTests extends Test {

  private object Actions extends Enumeration {
    type Action = Value
    val Stop, Skip, Restart, Empty = Value
  }

  private case class RestartCallException(x: Int) extends Exception
  private case class StopCallException(x: Int) extends Exception
  private case class SkipCallException(x: Int) extends Exception

  private val skipCount = new AtomicInteger(0)
  private val stopCount = new AtomicInteger(0)
  private val restartCount = new AtomicInteger(0)
  private val preRestartCount = new AtomicInteger(0)
  @volatile
  private var isStopped = false

  private class ParentActor extends Actor {

    val _child: Actor = new ChildActor
    var _childRef: ActorRef = null

    override def applyStrategy(throwable: Throwable, failedMessage: Option[Any],
                               count: Int): ActorStrategies.Value = {
      throwable match {
        case RestartCallException(_) =>
          if (count > 3) {
            ActorStrategies.Skip
          } else {
            restartCount.incrementAndGet()
            ActorStrategies.Restart
          }
        case StopCallException(_) =>
          stopCount.incrementAndGet()
          ActorStrategies.Stop
        case SkipCallException(_) =>
          skipCount.incrementAndGet()
          ActorStrategies.Skip
      }
    }

    override def receive: Receive = {
      case _ =>
        _childRef = spawn(_child, "child")
    }
  }

  private class ChildActor extends Actor {
    override def receive: Receive = {
      case x: Actions.Value =>
        x match {
          case Actions.Restart => throw RestartCallException(0)
          case Actions.Stop => throw StopCallException(1)
          case Actions.Skip => throw SkipCallException(2)
          case _ =>
        }
    }

    override def postStop(): Unit = {
      isStopped = true
    }

    override def preRestart(): Unit = {
      preRestartCount.incrementAndGet()
    }
  }

  test("actor strategies") {
    val system = ActorSystem("test-system")
    val parent = new ParentActor
    val parentActor = system.spawn(parent, "parent")
    assert1(parent._child._state == ActorStates.UnInitialized)

    parentActor.tell(Actions.Empty, parentActor)

    assert1(parent._child._state == ActorStates.Live, "child actor is not run")

    assert(parent._child._context.address.path == "test-system/parent/child")

    val ch = parent._childRef

    // skip first
    parentActor.tell(Actions.Skip, ch)
    parentActor.tell(Actions.Skip, ch)
    parentActor.tell(Actions.Skip, ch)

    parentActor.tell(Actions.Empty, ch)
    parentActor.tell(Actions.Restart, ch)
    parentActor.tell(Actions.Restart, ch)

    parentActor.tell(Actions.Stop, ch)
    parentActor.tell(Actions.Stop, ch)

    assert1(skipCount.get() == 3, s"${skipCount.get()} given")
    assert1(preRestartCount.get() == 8, s"${preRestartCount.get()} given")
    assert1(stopCount.get() == 1, s"${stopCount.get()} given")
    assert1(isStopped, "actor in Live state")

    system.stop()
  }

}
