package com.truerss.actorika

import com.truerss.actorika.support._
import org.slf4j.LoggerFactory

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import java.util.concurrent.{ArrayBlockingQueue => ABQ}
import java.util.concurrent.{Executor, Executors, ThreadFactory}

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

  private val systemRunner = Executors.newCachedThreadPool(
    threadFactory(s"$systemName-runner")
  )

  private [actorika] val systemActor = CommonActors.systemActor(setup)

  private[actorika] val scheduler: Scheduler =
    new Scheduler(threadFactory(s"$systemName-scheduler"))

  start()

  private [actorika] var _onTerminationFunction = { () => }

  def registerOnTermination(f : () => Unit): Unit = {
    _onTerminationFunction = f
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
    try {
      actor.preStart()
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
    systemActor.setContext(
      state = ActorStates.Live,
      parent = null,
      me = new ActorRef(Address(systemName), MailBox(1), this, isSystemRoot = true),
      system = this,
      name = s"$systemName-actor"
    )
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
        ra.stop()

      case None =>
        logger.warn(s"You're trying to stop Actor(${ref.path}), which is not exist in ActorSystem($systemName)")
    }
  }

  def stop(): Unit = {
    logger.debug(s"Stop ActorSystem($systemName)")
    systemActor.stop()
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


case class ActorInitializationError(message: String) extends Exception

case class ActorSetup(
                       handleDeadLetters: Boolean,
                       maxRestartCount: Int,
                       defaultExecutor: Executor,
                       exceptionOnStart: Boolean,
                       defaultStrategy: ActorStrategies.Value,
                       defaultMailboxSize: Int
                     )

object ActorSetup {
  val default: ActorSetup = new ActorSetup(
    handleDeadLetters = true,
    maxRestartCount = 100,
    defaultExecutor = null,
    exceptionOnStart = false,
    defaultStrategy = ActorStrategies.Skip,
    defaultMailboxSize = 100
  )
}