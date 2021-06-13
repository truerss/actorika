package io.truerss.actorika

import org.slf4j.LoggerFactory

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import java.util.concurrent.{Executor, ExecutorService, Executors, ThreadFactory, ConcurrentHashMap => CHM, ConcurrentLinkedQueue => CLQ}
import java.util.{ArrayList => AL}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._
import scala.reflect.runtime.universe._

case class ActorSystem(systemName: String, settings: ActorSystemSettings) {

  import ActorSystem._

  val address: Address = Address(systemName)

  private val globalCounter = new AtomicLong(0)

  private[actorika] var _deadLettersHandler: DeadLettersHandler =
    new DefaultDeadLettersHandler

  private[actorika] var _onTerminationFunction = { () => }

  @volatile private[actorika] var stopSystem = false

  private def createDefaultExecutor: Executor = {
    Executors.newFixedThreadPool(cores,
      threadFactory(s"$systemName-default")
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

  private val systemActor: RealActor = RealActor(
    actor = Actor.empty,
    ref = systemRef,
    system = this
  )

  private[actorika] val world: CHM[String, RealActor] = new CHM[String, RealActor]()

  private val _defaultStrategy: StrategyF = (ex: Throwable, fm: Option[Any], c: Int) =>
    ActorStrategies.Stop

  private[actorika] def resolveStrategy(ref: ActorRef): Vector[StrategyF] = {
    resolveStrategy(ref, Vector.empty[StrategyF])
  }
  // root
//  world.put(systemRef.path, systemActor)

  def find(path: String): Option[ActorRef] = {
    world.values().asScala.find(_.ref.path.contains(path)).map(_.ref)
  }

  private def resolveStrategy(ref: ActorRef, xs: Vector[StrategyF]): Vector[StrategyF] = {
    Option(world.get(ref.path)) match {
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
    Option(world.putIfAbsent(realActor.ref.path, realActor)) match {
      case Some(prev) if prev == realActor =>
        realActor
      case None =>
        realActor.tryToStart()
        realActor
      case _ =>
        throw new IllegalArgumentException(s"Actor#${realActor.ref.path} already present")
    }
  }

  private[actorika] def spawn(actor: Actor,
                              name: String,
                              parent: ActorRef
                             ): ActorRef = {
    // push into world
    val realActor = allocate(actor, name, parent)
    if (parent.isSystemRef) {
      // ok
      systemActor.actor._children.put(realActor.ref.path, realActor)
    }
    realActor.ref
  }

  private[actorika] def findParent(ref: ActorRef): Option[RealActor] = {
    Option(world.get(ref.path)) match {
      case Some(ra) =>
        val parent = ra.actor._parent
        if (parent.isSystemRef) {
          Some(systemActor)
        } else {
          Option(parent).map(x => world.get(x.path))
        }

      case None =>
        None
    }
  }

  private[actorika] def findMe(ref: ActorRef): Option[RealActor] = {
    Option(world.get(ref.path)) match {
      case Some(ra) => // fast check
        // todo pass ActorSystem !!!
        Some(ra)
      case None =>
        // find in actor's hierarchy
        world.values().asScala.to(LazyList)
          .map { x =>
            findMe(x, ref)
          }.collectFirst {
          case Some(r) => r
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
    Option(world.get(ref.path)) match {
      case Some(ra) =>
        ra.stop()
        rm(ref)

      case None =>
        logger.warn(s"You're trying to stop ${ref.path}-actor, which is not exist in the system")
    }
  }

  // todo remove after pure tree impl
  private[actorika] def rm(ref: ActorRef): Unit = {
    world.remove(ref.path)
  }

  /**
   * restart is: stop + start + clear mailbox
   * @param ref - actor reference
   */
  def restart(ref: ActorRef): Unit = {
    logger.debug(s"Restart ${ref.path}-actor")
    Option(world.get(ref.path)).foreach { x =>
      x.tryToRestart(Vector.empty, None)
    }
  }

  def subscribe[T](ref: ActorRef, klass: Class[T])(implicit _tag: TypeTag[T]): Unit = {
    Option(world.get(ref.path)).foreach { actor =>
      actor.subscribe(klass)
    }
  }

  def unsubscribe[T](ref: ActorRef, klass: Class[T])(implicit _tag: TypeTag[T]): Unit = {
    Option(world.get(ref.path)).foreach { actor =>
      actor.unsubscribe(klass)
    }
  }

  def unsubscribe(ref: ActorRef): Unit = {
    Option(world.get(ref.path)).foreach { actor =>
      actor.unsubscribe()
    }
  }

  def publish[T](message: T)(implicit _tag: TypeTag[T]): Unit = {
    var handled = false
    world.forEach { (_, actor) =>
      if (actor.canHandle(message)) {
        handled = true
        systemRef.send(actor.ref, message)
      }
    }
    if (!handled) {
      logger.warn(s"Can not publish: $message, there are no actors to handle the message")
      _deadLettersHandler.handle(message, systemRef, systemRef)
    }
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
          world.forEach { (_, actor) =>
            actor.tick()
          }
        }
      }
    })
  }

  def stop(): Unit = {
    logger.debug(s"Stop $systemName actor-system")
    world.values().forEach(ra => ra.stop())
    // check world.size == 0
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
