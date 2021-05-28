package io.truerss.actorika

import java.util.concurrent.{Executor, Executors, ThreadFactory, ConcurrentHashMap => CHM, ConcurrentLinkedQueue => CLQ}
import java.util.{ArrayList => AL}


case class ActorSystem(systemName: String) {

  private val address: Address = Address(systemName)

  private[actorika] val world: CHM[String, RealActor] = new CHM[String, RealActor]()

  private class ActorikaThreadFactory extends ThreadFactory {
    override def newThread(r: Runnable): Thread = {
      new Thread(r, systemName)
    }
  }

  private val cores = Runtime.getRuntime.availableProcessors()

  private val defaultExecutor: Executor = Executors.newFixedThreadPool(cores,
    new ActorikaThreadFactory()
  )

  private val systemRef: ActorSystem = this

  // no messages for processing
  private val system: ActorRef = ActorRef(
    address,
    isSystemRef = true,
    new CLQ[ActorMessage](new AL[ActorMessage](0))
  )

  // todo pass queue size and other settings
  def spawn(actor: Actor, name: String): ActorRef = {
    spawn(actor, name, isRestart = false)
  }

  private[actorika] def spawn(actor: Actor,
                              name: String,
                              parent: ActorRef,
                              isRestart: Boolean): ActorRef = {
    val tmpAddress = if (parent.isSystemRef) {
      // top
      address.merge(name)
    } else {
      parent.address.merge(name)
    }
    val tmpMailbox = new CLQ[ActorMessage]()
    val ref = ActorRef(tmpAddress, tmpMailbox)
    actor.setMe(ref)
    actor.setSystem(this)
    actor.setParent(parent)
    actor.withExecutor(defaultExecutor) // todo from setup

    val realActor = RealActor(actor, ref, systemRef)
    Option(world.putIfAbsent(tmpAddress.name, realActor)) match {
      case Some(prev) if prev == realActor =>
        ref
      case None =>
        var isDone = false
        var counter = 1 // from preRestart part
        while(!isDone) {
          try {
            if (isRestart) {
              actor.preRestart()
            }
            actor.preStart()
            isDone = true
          } catch {
            case ex: Throwable =>
              actor.applyRestartStrategy(ex, None, counter) match {
                case ActorStrategies.Stop =>
                  actor.postStop()
                  stop(ref)
                  isDone = true
                case ActorStrategies.Restart =>
              }
          }
          counter = counter + 1
        }

        ref
      case _ => throw new Exception(s"Actor#$name already present")
    }
  }

  protected def spawn(actor: Actor, name: String, isRestart: Boolean): ActorRef = {
    spawn(actor, name, system, isRestart)
  }

  def stop(ref: ActorRef): Unit = {
    // sync ?
    world.remove(ref.path)
    ref.associatedMailbox.clear()
  }

  def restart(actor: Actor, name: String): Unit = {
    // lock probably
    world.remove(name)
    spawn(actor, name, isRestart = true)
  }

  // def registerDeadLetterChannel: Received => From, Option(to)

  // todo in separate thread
  def run(): Unit = {
    // tick-tack event loop
    while (true) {
      world.forEach { (_, actor) =>
        actor.tick()
      }
    }
  }

  def send(to: ActorRef, msg: Any): Unit = {
    val message = ActorMessage(msg, to, system)
    to.associatedMailbox.add(message)
  }

  override def toString: String = s"ActorSystem($systemName)"

}

