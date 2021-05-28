package io.truerss.actorika

import java.util.concurrent.{Executor, Executors}

trait Actor {

  final type Receive = PartialFunction[Any, Unit]

  private var _me: ActorRef = null

  private var _executor: Executor = Executors.newSingleThreadExecutor()

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

  private var _parent: ActorRef = null

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
                           count: Int): ActorStrategies.Value = ActorStrategies.Stop

  // life cycle
  def preStart(): Unit = {}
  def postStop(): Unit = {}
  def preRestart(): Unit = {}

  def onUnhandled(msg: Any): Unit = {}

  def spawn(actor: Actor, name: String): ActorRef = {
    system.spawn(actor, name, me)
  }

  def stop(): Unit = {
    system.stop(me)
  }

  def stop(ref: ActorRef): Unit = {
    system.stop(ref)
  }

  override def toString: String = s"Actor(${me.path})"

}

object Actor {
  implicit class ActorRefExt(val to: ActorRef) extends AnyVal {
    def !(msg: Any)(implicit from: ActorRef): Unit = {
      from.send(to, msg)
    }
  }
}