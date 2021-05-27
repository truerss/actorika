package io.truerss.actorika

import java.util.concurrent.{ConcurrentHashMap => CHM}
import java.util.concurrent.{ConcurrentLinkedQueue => CLQ}
import java.util.{ArrayList => AL}

case class ActorSystem(systemName: String) {

  private val address: Address = Address(systemName)

  // no messages for processing
  private val systemRef: ActorRef = ActorRef(address, new CLQ[ActorMessage](
    new AL[ActorMessage](0))
  )

  private val world: CHM[String, RealActor] = new CHM[String, RealActor]()

  // todo pass queue size and other settings
  def spawn(actor: Actor, name: String): ActorRef = {
    spawn(actor, name, isRestart = false)
  }

  private def spawn(actor: Actor, name: String, isRestart: Boolean): ActorRef = {
    val tmpAddress = address.merge(Address(name))
    val tmpMailbox = new CLQ[ActorMessage]()
    val ref = ActorRef(tmpAddress, tmpMailbox)
    actor.setMe(ref)
    val realActor = RealActor(actor, ref, this)
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

  def restart(actor: Actor, name: String): Unit = {
    // lock probably
    world.remove(name)
    spawn(actor, name, isRestart = true)
  }

  def stop(ref: ActorRef): Unit = {
    // sync ?
    world.remove(ref.path)
    ref.associatedMailbox.clear()
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
    val message = ActorMessage(msg, to, systemRef)
    to.associatedMailbox.add(message)
  }

  override def toString: String = s"ActorSystem($systemName)"

}

