package io.truerss.actorika

import java.util.concurrent.{
  ExecutorService,
  Executors,
  ConcurrentHashMap => CHM,
  ConcurrentLinkedQueue => CLQ
}
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

  def applyStrategy(throwable: Throwable,
                    failedMessage: Option[Any],
                    restartCount: Int): ActorStrategies.Value = {
    system.setup.defaultStrategy
  }

  private [actorika] var _me: ActorRef = null

  def me: ActorRef = _context.me

  private [actorika] var _context: Context = null

  protected def system: ActorSystem = _context.system

  protected def scheduler: Scheduler = system.scheduler

  protected implicit def current: ActorRef = me

  protected def children: Iterable[ActorRef] = _children.asScala.values.map(_.me)

  private [actorika] def setMe(ref: ActorRef): Unit = {
    _me = ref
  }

  private [actorika] var _children: CHM[Address, Actor] = new CHM[Address, Actor]()

  private [actorika] def setContext(state: ActorStates.Value,
                                    parent: Actor,
                                    me: ActorRef,
                                    system: ActorSystem,
                                    name: String
                                 ): Unit = {
    _context = Context(
      name = name,
      parent = parent,
      me = me,
      system = system
    )
    if (parent != null) {
      parent._children.put(me.address, this)
    }
    _state = state
  }

  private var _sender: ActorRef = null

  private [actorika] def setSender(ref: ActorRef): Unit = {
    _sender = ref
  }

  protected def sender: ActorRef = _sender

  private [actorika] var _executor: ExecutorService = Executors.newSingleThreadExecutor()

  def executor: ExecutorService = _executor

  def preStart(): Unit = {}
  def postStop(): Unit = {}
  def preRestart(): Unit = {}

  def onUnhandled(message: Any): Unit = {}

  final protected def become(next: Receive): Unit = {
    _currentReceive = next
  }

  private [actorika] def currentHandler: Receive = _currentReceive

  def receive: Receive

  def subscribe[T](klass: Class[T]): Unit = {
    subscriptions.add(klass)
  }

  def unsubscribe[T](klass: Class[T]): Unit = {
    subscriptions.remove(klass)
  }

  def unsubscribe(): Unit = {
    subscriptions.clear()
  }

  def canHandle[T](v: T)(implicit kTag: ClassTag[T]): Boolean = {
    subscriptions.forEach { x =>
      if (x.isAssignableFrom(kTag.runtimeClass)) {
        return true
      }
    }
    false
  }

  protected def spawn(child: Actor, name: String): ActorRef = {
    _context.system.spawn(child, name, this)
  }

  @volatile
  private var isBusy = false

  private [actorika] def tickMe(): Unit = {
    _state match {
      case ActorStates.Live =>
        while (isBusy) {}
        val message = me.mailBox.queue.take()
        tick(message)

      case ActorStates.Finished =>
        ActorSystem.logger.warn(s"Actor ${me.path} is finished, messages (total=${me.mailBox.queue.size}) will be skipped")

      case _ =>
        // todo unknown state warn
    }
  }

  def tick(message: ActorMessage, restartCount: Int = 0): Unit = {
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

  private [actorika] def stop(): Unit = {
    setState(ActorStates.Finished)
    ActorSystem.logger.debug(s"Stop $this")
    clearMailbox()
    _children.forEach { (_, x) =>
      x.stop()
    }
    Option(_context.parent).foreach(_._children.remove(me.address))
    call(() => postStop()) { ex =>
      ActorSystem.logger.warn(s"Failed to call `postStop` in Actor(${me.path})", ex)
    }
  }

  def call(f: () => Unit)(onError: Throwable => Unit): Unit = {
    try {
      f.apply()
    } catch {
      case ex: Throwable =>
        onError(ex)
    }
  }

  private [actorika] def clearMailbox(): Unit = {
    me.mailBox.clear
  }

  override def toString: String = {
    s"Actor(${me.path})"
  }
}

case class Context(
                    name: String,
                    parent: Actor,
                    me: ActorRef,
                    system: ActorSystem
                  ) {
  val address: Address = me.address
}