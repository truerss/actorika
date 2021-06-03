package io.truerss.actorika

import java.util.concurrent.{Executor, ConcurrentLinkedQueue => CLQ}
import scala.jdk.CollectionConverters

trait Actor {

  import CollectionConverters._

  final type Receive = PartialFunction[Any, Unit]

  @volatile private[actorika] var _state: ActorStates.ActorState =
    ActorStates.Uninitialized

  private[actorika] val _children = new CLQ[ActorRef]()

  protected[actorika] def moveStateTo(newState: ActorStates.ActorState): Unit = {
    _state = newState
  }

  protected def children: Iterable[ActorRef] = {
    _children.asScala
  }

  private var _me: ActorRef = null

  private var _executor: Executor = null

  protected implicit def current: ActorRef = me

  def withExecutor(ex: Executor): Actor = {
    _executor = ex
    this
  }

  def executor: Executor = _executor

  private[actorika] def setMe(ref: ActorRef): Unit = {
    _me = ref
  }

  protected def me: ActorRef = _me

  private var _sender: ActorRef = null

  private[actorika] def setSender(ref: ActorRef): Unit = {
    _sender = ref
  }

  protected def sender: ActorRef = _sender

  private[actorika] var _parent: ActorRef = null

  private[actorika] def setParent(ref: ActorRef): Unit = {
    _parent = ref
  }

  protected def parent(): ActorRef = _parent

  private var _system: ActorSystem = null

  private[actorika] def setSystem(s: ActorSystem): Unit = {
    _system = s
  }

  protected def system: ActorSystem = _system

  def receive: Receive

  def applyRestartStrategy(ex: Throwable,
                           failedMessage: Option[Any],
                           count: Int): ActorStrategies.Value = ActorStrategies.Parent

  private[actorika] def resolveStrategy(ex: Throwable,
                                        failedMessage: Option[Any],
                                        count: Int): ActorStrategies.Value = {
    applyRestartStrategy(ex, failedMessage, count) match {
      case ActorStrategies.Parent =>
        val tmp = system.resolveStrategy(me)
        // 0 is checked ^
        resolveAndApply(ex, failedMessage, count, 1, tmp)

      case x => x
    }
  }

  private def resolveAndApply(ex: Throwable,
                              failedMessage: Option[Any],
                              count: Int,
                              index: Int,
                              strategies: Vector[ActorSystem.StrategyF]
                             ): ActorStrategies.Value = {
    if (index >= strategies.size) {
      ActorStrategies.Stop
    } else {
      strategies(index).apply(ex, failedMessage, count) match {
        case ActorStrategies.Parent =>
          resolveAndApply(ex, failedMessage, count, index + 1, strategies)
        case x => x
      }
    }
  }

  // life cycle
  def preStart(): Unit = {}
  def postStop(): Unit = {}
  def preRestart(): Unit = {}

  def onUnhandled(msg: Any): Unit = {}

  def spawn(actor: Actor, name: String): ActorRef = {
    val ref = system.spawn(actor, name, me)
    _children.add(ref)
    ref
  }

  def stop(): Unit = {
    system.stop(me)
  }

  def stop(ref: ActorRef): Unit = {
    system.stop(ref)
  }

  private[actorika] def stop(path: String): Unit = {
    system.stop(path)
  }

  override def toString: String = s"Actor(${me.path}:${_state})"

}

object Actor {
  implicit class ActorRefExt(val to: ActorRef) extends AnyVal {
    def !(msg: Any)(implicit from: ActorRef): Unit = {
      from.send(to, msg)
    }
  }
}