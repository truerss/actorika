package io.truerss.actorika

import io.truerss.actorika.support._
import org.slf4j.LoggerFactory

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import java.util.concurrent.{Executors, ThreadFactory, ArrayBlockingQueue => ABQ}
import scala.concurrent.ExecutionContext

case class ActorSystem(systemName: String, setup: ActorSetup = ActorSetup.default)
  extends SubscriptionSupport
  with FindSupport
  with LikeAnActor
  with DeadLettersSupport {

  import ActorSystem._

  @volatile
  private [actorika] var isStopped: Boolean = false

  private [actorika] val delayQueue: ABQ[ActorRef] = new ABQ[ActorRef](1000)

  private final val askGen = new AtomicLong(0)

  private final val defaultActorNameGenerator = ActorNameGenerator.default

  val defaultExecutor: ExecutionContext = setup.defaultExecutor

  private val systemRunner = Executors.newCachedThreadPool(
    threadFactory(s"$systemName-runner")
  )

  private [actorika] val systemActor = CommonActors.systemActor(setup)
  systemActor.withExecutor(defaultExecutor)
  systemActor.setContext(
    state = ActorStates.Live,
    name = s"$systemName-actor",
    parent = null,
    me = new ActorRef(Address(systemName), MailBox(1), this, isSystemRoot = true),
    system = this
  )


  private[actorika] val scheduler: Scheduler =
    new Scheduler(threadFactory(s"$systemName-scheduler"))

  start()

  private [actorika] var _onTerminationFunction = { () => }

  def registerOnTermination(f : () => Unit): Unit = {
    _onTerminationFunction = f
  }

  def spawn(actor: Actor, generator: ActorNameGenerator): ActorRef = {
    spawn(actor, generator.next())
  }

  def spawn(actor: Actor): ActorRef = {
    spawn(actor, defaultActorNameGenerator.next())
  }

  def spawn(actor: Actor, name: String): ActorRef = {
    spawn(actor, name, systemActor)
  }

  private [actorika] def spawnAsk(actor: Actor): ActorRef = {
    spawn(actor, s"ask-${askGen.incrementAndGet()}")
  }

  private [actorika] def spawn(actor: Actor, name: String, parent: Actor): ActorRef = {
    val me = new ActorRef(Address(parent, name), MailBox(setup.defaultMailboxSize), this)
    actor.setContext(ActorStates.UnInitialized, parent, me, this, name)
    if (!actor.hasExecutor) {
      actor.withExecutor(defaultExecutor)
    }
    try {
      actor.callPreStart()
      actor.setState(ActorStates.Live)
      logger.debug(s"Start ${me.path} actor")
      me
    } catch {
      case ex: Throwable =>
        logger.warn(s"Failed to initialize: Actor(${me.path})", ex)
        throw ActorInitializationError(s"Failed to initialize $name")
    }
  }

  private [actorika] def tick(ref: ActorRef): Unit = {
    delayQueue.put(ref)
  }

  def start(): Unit = {
    systemRunner.execute(() => {
      while (!isStopped) {
        val ref = delayQueue.take()
        findActor(ref.path) match {
          case Some(actor) =>
            actor.tickMe()
          case None =>
            logger.warn(s"Actor ${ref.address.path} was not found in $systemName")
        }
      }
    })
  }

  // @note any exceptions in `stop` will be ignored
  def stop(ref: ActorRef): Unit = {
    logger.debug(s"Stop ${ref.path}")
    findMe(ref) match {
      case Some(ra) =>
        ra.callStop()

      case None =>
        logger.warn(s"You're trying to stop Actor(${ref.path}), which is not exist in ActorSystem($systemName)")
    }
  }

  def stop(): Unit = {
    logger.debug(s"Stop ActorSystem($systemName)")
    systemActor.callStop()
    systemRunner.shutdown()
    scheduler.stop()
    try {
      _onTerminationFunction.apply()
    } catch {
      case ex: Throwable =>
        logger.warn(s"Error in stop ActorSystem($systemName)", ex)
    }
    isStopped = true
  }

}

object ActorSystem {

  private [actorika] val logger = LoggerFactory.getLogger(getClass)

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