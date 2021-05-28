package io.truerss.actorika

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executor, Executors, ThreadFactory, ConcurrentHashMap => CHM, ConcurrentLinkedQueue => CLQ}
import java.util.{ArrayList => AL}
import scala.reflect.runtime.universe._

case class ActorSystem(systemName: String) {

  val address: Address = Address(systemName)

  private[actorika] val world: CHM[String, RealActor] = new CHM[String, RealActor]()

  private class ActorikaThreadFactory extends ThreadFactory {
    private final val counter = new AtomicInteger()
    override def newThread(r: Runnable): Thread = {
      val t = new Thread(r)
      t.setName(s"$systemName-pool-${counter.incrementAndGet()}")
      t
    }
  }

  private val cores = Runtime.getRuntime.availableProcessors()

  private val defaultExecutor: Executor = Executors.newFixedThreadPool(cores,
    new ActorikaThreadFactory()
  )

  // no messages for processing
  private val systemRef: ActorRef = ActorRef(
    address,
    isSystemRef = true,
    new CLQ[ActorMessage](new AL[ActorMessage](0))
  )

  // todo pass queue size and other settings
  def spawn(actor: Actor, name: String): ActorRef = {
    spawn(actor, name, systemRef)
  }

  private[actorika] def spawn(actor: Actor,
                              name: String,
                              parent: ActorRef): ActorRef = {
    val tmpAddress = defineAddress(name, parent)
    val tmpMailbox = new CLQ[ActorMessage]()
    val ref = ActorRef(tmpAddress, tmpMailbox)
    actor.setMe(ref)
    actor.setSystem(this)
    actor.setParent(parent)
    actor.withExecutor(defaultExecutor) // todo from setup

    val realActor = RealActor(actor, ref, this)
    Option(world.putIfAbsent(tmpAddress.name, realActor)) match {
      case Some(prev) if prev == realActor =>
        ref
      case None =>
        actor.preStart()
        ref
      case _ => throw new Exception(s"Actor#$name already present")
    }
  }

  private def defineAddress(name: String,
                            parent: ActorRef): Address = {
    if (parent.isSystemRef) {
      // top
      address.merge(name)
    } else {
      parent.address.merge(name)
    }
  }

  def stop(ref: ActorRef): Unit = {
    // sync ?
    Option(world.remove(ref.path)) match {
      case Some(actor) =>
        ref.associatedMailbox.clear()
        actor.actor.postStop()
      case _ =>
        // actor was not found, ignore, warning ?
    }
  }

  // restart = stop + start + clear mailbox nothing more
  // todo also strategy check
  def restart(actor: RealActor): Unit = {
    // lock probably
    actor.actor.preRestart()
    actor.ref.associatedMailbox.clear()
    actor.actor.postStop()
    actor.actor.preStart()
  }

  def restart(ref: ActorRef): Unit = {
    Option(world.get(ref.path)).foreach { x =>
      restart(x)
    }
  }


  // def registerDeadLetterChannel: Received => From, Option(to)

  def subscribe[T](ref: ActorRef, klass: Class[T])(implicit _tag: TypeTag[T]): Unit = {
    Option(world.get(ref.path)).foreach { actor =>
      actor.subscribe(klass)
    }
  }

  def publish[T](message: T)(implicit _tag: TypeTag[T]): Unit = {
    world.forEach { (_, actor) =>
      if (actor.canHandle(message)) {
        systemRef.send(actor.ref, message)
      }
    }
  }

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
    val message = ActorMessage(msg, to, systemRef)
    to.associatedMailbox.add(message)
  }

  override def toString: String = s"ActorSystem($systemName)"

}

