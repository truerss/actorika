package io.truerss.actorika

import java.util.concurrent.{Executor, Executors, ThreadFactory, ConcurrentHashMap => CHM, ConcurrentLinkedQueue => CLQ}
import java.util.{ArrayList => AL}

trait Spawn {
  protected val address: Address
  protected val defaultExecutor: Executor
  protected val systemRef: ActorSystem
  protected val world: CHM[String, RealActor] = new CHM[String, RealActor]()

  // todo pass queue size and other settings
  def spawn(actor: Actor, name: String): ActorRef = {
    spawn(actor, name, isRestart = false)
  }

  protected def spawn(actor: Actor, name: String, isRestart: Boolean): ActorRef = {
    val tmpAddress = address.merge(Address(name))
    val tmpMailbox = new CLQ[ActorMessage]()
    val ref = ActorRef(tmpAddress, tmpMailbox, defaultExecutor, systemRef)
    actor.setMe(ref)
    actor.withExecutor(defaultExecutor)

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

}
