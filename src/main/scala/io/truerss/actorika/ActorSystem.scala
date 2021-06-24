package io.truerss.actorika

import org.slf4j.LoggerFactory

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import java.util.concurrent.{Executor, ExecutorService, Executors, ThreadFactory, ConcurrentLinkedQueue => CLQ}
import java.util.{ArrayList => AL}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

case class ActorSystem(systemName: String, settings: ActorSystemSettings) {

  import ActorSystem._

  private val globalCounter = new AtomicLong(0)

  private[actorika] var _deadLettersHandler: DeadLettersHandler =
    new DefaultDeadLettersHandler

  private[actorika] var _onTerminationFunction = { () => }

  @volatile private[actorika] var stopSystem = false

  val address: Address = Address(systemName)

  private def createDefaultExecutor: Executor = {
    Executors.newFixedThreadPool(cores,
      threadFactory(s"$systemName-worker-pool")
    )
  }

  private val defaultExecutor: Executor = Option(settings.defaultExecutor)
    .getOrElse(createDefaultExecutor)

  val executor: Executor = defaultExecutor

  implicit val context: ExecutionContextExecutor = ExecutionContext.fromExecutor(executor)

  private val runner: Executor = Executors.newSingleThreadExecutor(
    threadFactory(s"$systemName-runner")
  )

  private[actorika] val scheduler: Scheduler =
    new Scheduler(threadFactory(s"$systemName-scheduler"))

  // no messages for processing
  private val systemRef: ActorRef = ActorRef(
    address,
    isSystemRef = true,
    new CLQ[ActorMessage](new AL[ActorMessage](0))
  )

  private[actorika] val systemActor: RealActor = RealActor(
    actor = Actor.empty,
    ref = systemRef,
    system = this
  )

  private[actorika] def isEmpty: Boolean = systemActor.children.isEmpty

  // root size
  private[actorika] def size: Int = systemActor.children.size

  private val _defaultStrategy: StrategyF = (ex: Throwable, fm: Option[Any], c: Int) =>
    ActorStrategies.Stop

  private[actorika] def resolveStrategy(ref: ActorRef): Vector[StrategyF] = {
    resolveStrategy(ref, Vector.empty[StrategyF])
  }

  def find(path: String): Option[ActorRef] = {
    findRealActor(path).map(_.ref)
  }

  private[actorika] def findRealActor(path: String): Option[RealActor] = {
    systemActor.children.asScala.find { case (p, _) => p.contains(path) } match {
      case Some((_, ra)) =>
        Some(ra)
      case None =>
        systemActor.children.values().asScala.to(LazyList)
          .map { x =>
            findRealActor(x, path)
          }.collectFirst {
          case Some(r) => r
        }
    }
  }

  private[actorika] def findRealActor(ra: RealActor, path: String): Option[RealActor] = {
    if (ra.ref.path.contains(path)) {
      Some(ra)
    } else {
      val chs = ra.children
      chs.asScala.find { case (p, _) => p.contains(path) } match {
        case Some((_, ra)) => Some(ra)
        case None =>
          chs.values().asScala.to(LazyList)
            .map { x => findRealActor(x, path) }
            .collectFirst {
              case Some(r) => r
            }
      }
    }
  }

  private def resolveStrategy(ref: ActorRef, xs: Vector[StrategyF]): Vector[StrategyF] = {
    findMe(ref) match {
      case Some(ra) if ra.ref.isSystemRef =>
        xs :+ _defaultStrategy
      case Some(ra) =>
        val tmp = (ex: Throwable, fm: Option[Any], c: Int) =>
          ra.actor.applyRestartStrategy(ex, fm, c)
        resolveStrategy(ra.actor._parent, xs :+ tmp)
      case None =>
        // system here
        xs :+ _defaultStrategy
    }
  }

  def registerDeadLetterHandler(handler: (Any, ActorRef, ActorRef) => Unit): Unit = {
    val tmp = new DeadLettersHandler {
      override def handle(message: Any, from: ActorRef, to: ActorRef): Unit = {
        handler.apply(message, from, to)
      }
    }
    _deadLettersHandler = tmp
  }

  def registerDeadLetterHandler(handler: DeadLettersHandler): Unit = {
    _deadLettersHandler = handler
  }

  def spawn(actor: Actor, generator: ActorNameGenerator): ActorRef = {
    spawn(actor, generator.next(globalCounter.getAndIncrement()), systemRef)
  }

  def spawn(actor: Actor, name: String): ActorRef = {
    spawn(actor, name, systemRef)
  }

  def spawn(actor: Actor): ActorRef = {
    spawn(actor, ActorNameGenerator.default.next(globalCounter.getAndIncrement()), systemRef)
  }

  private[actorika] def allocate(actor: Actor,
                              name: String,
                              parent: ActorRef
                             ): RealActor = {
    val tmpAddress = allocateAddress(name, parent)
    val tmpMailbox = new CLQ[ActorMessage]()
    val ref = ActorRef(tmpAddress, tmpMailbox)
    actor.setMe(ref)
    actor.setSystem(this)
    actor.setParent(parent)
    if (actor.executor == null) {
      actor.withExecutor(defaultExecutor)
    }
    val realActor = RealActor(
      actor = actor,
      ref = ref,
      system = this
    )
    findMe(parent) match {
      case Some(p) =>
        Option(p.actor._children.putIfAbsent(realActor.ref.path, realActor)) match {
          case Some(prev) if prev == realActor =>
            realActor
          case None =>
            realActor.tryToStart()
            if (realActor.isStopped) {
              p.actor._children.remove(realActor.ref.path)
              val message = s"Failed to start actor: ${ref.path} under ${parent.path}"
              logger.warn(message)
              if (settings.exceptionOnStart) {
                throw new RuntimeException(message)
              }
            }
            realActor
          case _ =>
            throw new IllegalArgumentException(s"Actor#${realActor.ref.path} already present")
        }
      case None =>
        throw new IllegalArgumentException(s"Actor#${parent.path} is not exist")
    }

  }

  private[actorika] def spawn(actor: Actor,
                              name: String,
                              parent: ActorRef
                             ): ActorRef = {
    allocate(actor, name, parent).ref
  }

  private[actorika] def findParent(ref: ActorRef): Option[RealActor] = {
    findMe(ref) match {
      case Some(ra) =>
        val parent = ra.actor._parent
        if (parent.isSystemRef) {
          Some(systemActor)
        } else {
          Option(parent).flatMap(x => findMe(x))
        }

      case None =>
        None
    }
  }

  private[actorika] def findMe(ref: ActorRef): Option[RealActor] = {
    if (ref.isSystemRef) {
      Some(systemActor)
    } else {
      Option(systemActor.children.get(ref.path)) match {
        case Some(ra) =>
          Some(ra)
        case None =>
          systemActor.children.values().asScala.to(LazyList)
            .map { x =>
              findMe(x, ref)
            }.collectFirst {
            case Some(r) => r
          }
      }
    }
  }

  private def findMe(ra: RealActor, ref: ActorRef): Option[RealActor] = {
    if (ra.ref == ref) {
      Some(ra)
    } else {
      val chs = ra.actor._children
      if (chs.contains(ref.path)) {
        Option(chs.get(ref.path))   // find in children
      } else {
        // ok try to find deeper
        val r=  chs.values().asScala.to(LazyList)
          .map { x => findMe(x, ref) }
          .collectFirst {
            case Some(r) => r
          }
        r
      }
    }
  }

  // @note any exceptions in `stop` will be ignored
  def stop(ref: ActorRef): Unit = {
    logger.debug(s"Stop ${ref.path}")
    findMe(ref) match {
      case Some(ra) =>
        ra.stop()

      case None =>
        logger.warn(s"You're trying to stop ${ref.path}-actor, which is not exist in the system")
    }
  }

  /**
   * restart is: stop + start + clear mailbox
   * @param ref - actor reference
   */
  private[actorika] def restart(ref: ActorRef): Unit = {
    logger.debug(s"Restart ${ref.path}")
    findMe(ref).foreach { x =>
      x.tryToRestart(Vector.empty, None)
    }
  }

  def subscribe[T](ref: ActorRef, klass: Class[T]): Unit = {
    findMe(ref).foreach { actor =>
      actor.subscribe(klass)
    }
  }

  def unsubscribe[T](ref: ActorRef, klass: Class[T]): Unit = {
    findMe(ref).foreach { actor =>
      actor.unsubscribe(klass)
    }
  }

  def unsubscribe(ref: ActorRef): Unit = {
    findMe(ref).foreach { actor =>
      actor.unsubscribe()
    }
  }

  def publish[T](message: T)(implicit kTag: ClassTag[T]): Unit = {
    val handlers = systemActor.children.values().asScala.to(LazyList)
      .map { x =>
        canHandle(x, message)
      }.collect {
      case xs if xs.nonEmpty => xs
    }.flatten

    if (handlers.isEmpty) {
      logger.warn(s"Can not publish: ${message.getClass}, there are no actors to handle the message")
      _deadLettersHandler.handle(message, systemRef, systemRef)
    } else {
      handlers.foreach { h => systemRef.send(h.ref, message) }
    }
  }

  private def canHandle[T](ra: RealActor, message: T)(implicit kTag: ClassTag[T]): LazyList[RealActor] = {
    val root = if (ra.canHandle(message)(kTag)) {
      Some(ra)
    } else {
      None
    }
    val inChs = ra.children.asScala.values.to(LazyList)
      .map { x => canHandle(x, message)(kTag) }
      .collect {
        case xs if xs.nonEmpty => xs
      }
    root.map { x => inChs.flatten :+ x }.getOrElse(inChs.flatten)
  }


  def send(to: ActorRef, msg: Any): Unit = {
    systemRef.send(to, msg)
  }

  def ask(to: ActorRef, msg: Any)(implicit waitTime: FiniteDuration): Future[Any] = {
    systemRef.ask(to, msg)
  }

  // tick-tack event loop
  def start(): Unit = {
    logger.debug(s"Start $systemName actor-system")
    runner.execute(new Runnable {
      override def run(): Unit = {
        while (!stopSystem) {
          systemActor.children.forEach { (_, actor) =>
            actor.tick()
          }
        }
      }
    })
  }

  def stop(): Unit = {
    logger.debug(s"Stop $systemName actor-system")
    systemActor.stop()
    scheduler.stop()
    defaultExecutor.asInstanceOf[ExecutorService].shutdown()
    runner.asInstanceOf[ExecutorService].shutdown()
    _onTerminationFunction.apply()
    stopSystem = true
  }

  def registerOnTermination(f : () => Unit): Unit = {
    _onTerminationFunction = f
  }

  private def allocateAddress(name: String,
                            parent: ActorRef): Address = {
    if (!Address.isValid(name)) {
      throw new IllegalArgumentException(
        s"Invalid actor name: '$name', name must contains only: ${Address.rx} characters"
      )
    }
    if (parent.isSystemRef) {
      // top
      address.merge(name)
    } else {
      parent.address.merge(name)
    }
  }

  override def toString: String = s"ActorSystem($systemName)"

}

object ActorSystem {

  def apply(name: String): ActorSystem = new ActorSystem(name, ActorSystemSettings.default)

  private val cores = Runtime.getRuntime.availableProcessors()

  private[actorika] val logger = LoggerFactory.getLogger(getClass)

  private[actorika] type StrategyF = (Throwable, Option[Any], Int) => ActorStrategies.Value

  private class ActorikaThreadFactory(name: String) extends ThreadFactory {
    private final val counter = new AtomicInteger()
    override def newThread(r: Runnable): Thread = {
      val t = new Thread(r)
      t.setName(s"$name-${counter.incrementAndGet()}")
      t
    }
  }

  def threadFactory(name: String): ThreadFactory = {
    new ActorikaThreadFactory(s"$name-pool")
  }
}
