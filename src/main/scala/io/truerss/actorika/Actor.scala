package io.truerss.actorika

import java.util.concurrent.{ConcurrentHashMap => CHM, ConcurrentLinkedQueue => CLQ}
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

trait Actor {

  final type Receive = PartialFunction[Any, Unit]

  private [actorika] val subscriptions = new CLQ[Class[_]]()

  @volatile
  private [actorika] var _state: ActorStates.Value = ActorStates.UnInitialized

  @volatile
  private [actorika] var _currentReceive: Receive = receive

  private [actorika] def setState(newState: ActorStates.Value): Unit = {
    _state = newState
  }

  protected var _me: ActorRef = null

  protected def me: ActorRef = context.me

  private [actorika] def callMe: ActorRef = me

  protected implicit def current: ActorRef = me

  private [actorika] var _context: Context = null

  protected def system: ActorSystem = _context.system

  protected lazy val scheduler: Scheduler = system.scheduler

  protected def children: Iterable[ActorRef] = _children.asScala.values.map(_.me)

  protected def context: Context = _context

  protected def parent: ActorRef = _context.parent.me

  private [actorika] val _children: CHM[Address, Actor] = new CHM[Address, Actor]()

  private var _sender: ActorRef = null

  protected def sender: ActorRef = _sender

  private [actorika] var _executor: ExecutionContext = null

  protected def executor: ExecutionContext = _executor

  private var _sleepTimeMillis = 100L

  protected def applyStrategy(throwable: Throwable,
                    failedMessage: Option[Any],
                    restartCount: Int): ActorStrategies.Value = {
    system.setup.defaultStrategy
  }

  def withExecutor(ec: ExecutionContext): Unit = {
    _executor = ec
  }

  private [actorika] def hasExecutor: Boolean = {
    _executor != null
  }

  private [actorika] def setContext(state: ActorStates.Value,
                                    parent: Actor,
                                    me: ActorRef,
                                    system: ActorSystem,
                                    name: String
                                 ): Unit = {
    _me = me
    _context = Context(
      name = name,
      parent = parent,
      me = me,
      system = system
    )
    _sleepTimeMillis = system.setup.defaultWaitTime.toMillis
    if (parent != null) {
      parent._children.put(me.address, this)
    }
    _state = state
  }

  private [actorika] def setSender(ref: ActorRef): Unit = {
    _sender = ref
  }

  private[actorika] def callPreStart(): Unit = preStart()

  protected def preStart(): Unit = {}
  protected def postStop(): Unit = {}
  protected def preRestart(): Unit = {}

  protected def onUnhandled(message: Any): Unit = {}

  final protected def become(next: Receive): Unit = {
    _currentReceive = next
  }

  private [actorika] def currentHandler: Receive = _currentReceive

  def receive: Receive

  protected [actorika] final def subscribe[T](klass: Class[T]): Unit = {
    subscriptions.add(klass)
  }

  protected [actorika] final def unsubscribe[T](klass: Class[T]): Unit = {
    subscriptions.remove(klass)
  }

  protected [actorika] final def unsubscribeAll(): Unit = {
    subscriptions.clear()
  }

  private [actorika] def canHandle[T](v: T)(implicit kTag: ClassTag[T]): Boolean = {
    subscriptions.forEach { x =>
      if (x.isAssignableFrom(kTag.runtimeClass)) {
        return true
      }
    }
    false
  }

  protected final def spawn(child: Actor, name: String): ActorRef = {
    _context.system.spawn(child, name, this)
  }

  @volatile
  private var isBusy = false

  private [actorika] def tickMe(): Unit = {
    _state match {
      case ActorStates.Live =>
        while (isBusy) {
          Thread.sleep(_sleepTimeMillis)
        }
        val message = _me.mailBox.queue.take()
        tick(message)

      case ActorStates.Finished =>
        ActorSystem.logger.warn(s"Actor ${me.path} is finished, messages (total=${me.mailBox.queue.size}) will be skipped")

      case _ =>
        ActorSystem.logger.error(s"Actor ${me.path} is UnInitialized")
        throw new IllegalStateException(s"$this can not handle messages in UnInitialized state")
    }
  }

  private def tick(message: ActorMessage, restartCount: Int = 0): Unit = {
    setSender(message.from)
    isBusy = true
    val current = message.message
    val handler = currentHandler

    if (handler.isDefinedAt(current)) {
      message match {
        case tell: ActorTellMessage =>
          callInExecutor(tell, handler, restartCount)

        case ActorAskMessage(_, to, from, timeout, promise) =>
          val anon = CommonActors.askActor(current, timeout, promise)
          val ref = system.spawnAsk(anon)
          from.tell(StartAsk, ref)
          ref.tell(current, to)
          isBusy = false
      }
    } else {
      if (message.isKill) {
        ActorSystem.logger.debug(s"Kill message given, $this will be stopped immediately, ${me.mailBox.size} messages lost.")
        stop()
      } else {
        onUnhandled(message.message)
      }
      isBusy = false
    }
  }

  private def callInExecutor(message: ActorTellMessage, handler: Receive, restartCount: Int): Unit = {
    val current = message.message
    val max = _context.system.setup.maxRestartCount
    executor.execute(() => {
      try {
        handler.apply(current)
        isBusy = false
      } catch {
        case throwable: Throwable if _context.parent != null =>
          val strategy = _context.parent.applyStrategy(
            throwable = throwable,
            failedMessage = Some(current),
            restartCount = restartCount
          )

          ActorSystem.logger.debug(s"Apply $strategy in Actor(${me.path}), count=$restartCount", throwable)

          strategy match {
            case ActorStrategies.Restart =>
              if (restartCount < max) {
                tryToRestart(message, restartCount + 1)
              } else {
                ActorSystem.logger.debug(s"Overlimit in restarts (default=$max), ${message.message} will be skipped in Actor(${me.path})")
                isBusy = false
              }

            case ActorStrategies.Stop =>
              stop()

            case ActorStrategies.Skip =>
              isBusy = false
              ActorSystem.logger.warn(s"Message ${message.message} from ${message.from} skipped in $this")
          }
        case _: Throwable =>
          ActorSystem.logger.error(s"Unknown actor $this for handling ${message.message}")
          stop()
      }
    })
  }

  private [actorika] def tryToRestart(message: ActorMessage, count: Int): Unit = {
    become(receive)
    call(() => preRestart()) { ex =>
      ActorSystem.logger.warn(s"Failed to call `preRestart` in Actor(${me.path})", ex)
    }
    tick(message, count)
  }

  private[actorika] def callStop(): Unit = {
    stop()
  }

  protected def stop(ref: ActorRef): Unit = {
    Option(_children.get(ref.address)) match {
      case Some(child) =>
        child.stop()

      case None =>
        ActorSystem.logger.warn(s"Can not stop the actor: ${ref.address}")
    }
  }

  protected def stop(): Unit = {
    setState(ActorStates.Finished)
    ActorSystem.logger.debug(s"Stop $this")
    _me.clear()
    _children.forEach { (_, x) =>
      x.stop()
    }
    Option(_context.parent).foreach(_._children.remove(me.address))
    call(() => postStop()) { ex =>
      ActorSystem.logger.warn(s"Failed to call `postStop` in Actor(${me.path})", ex)
    }
  }

  private def call(f: () => Unit)(onError: Throwable => Unit): Unit = {
    try {
      f.apply()
    } catch {
      case ex: Throwable =>
        onError(ex)
    }
  }

  override def toString: String = {
    s"Actor(${me.path})"
  }
}

case class Context(
                    name: String,
                    parent: Actor,
                    private[actorika] val me: ActorRef,
                    system: ActorSystem
                  ) {
  val address: Address = me.address
}