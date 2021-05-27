package io.truerss.actorika

import java.util.concurrent.{Executor, Executors}

/**
 * start actor = start infinite while loop ???
 *
 * @param name
 */

trait Actor {

  final type Receive = PartialFunction[Any, Unit]

  private var _me: ActorRef = null

  private var _executor: Executor = Executors.newCachedThreadPool()

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

  protected var _sender: ActorRef = null

  private[actorika] def setSender(ref: ActorRef): Unit = {
    _sender = ref
  }

  protected def sender: ActorRef = _sender

  def receive: Receive

  def applyRestartStrategy(ex: Throwable,
                           failedMessage: Option[Any],
                           count: Int): ActorStrategies.Value = ActorStrategies.Stop

  // life cycle
  def preStart(): Unit = {}
  def postStop(): Unit = {}
  def preRestart(): Unit = {}

  def onUnhandled(msg: Any): Unit = {}

}

object Actor {
  implicit class ActorRefExt(val to: ActorRef) extends AnyVal {
    def !(msg: String)(implicit from: ActorRef): Unit = {
      from.send(to, msg)
    }
  }
}