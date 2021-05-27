package io.truerss.actorika

import java.util.concurrent.{Executor, ConcurrentLinkedQueue => CLQ}

case class ActorRef(
                     address: Address,
                     private[actorika] val associatedMailbox: CLQ[ActorMessage],
                     protected override val defaultExecutor: Executor,
                     protected override val systemRef: ActorSystem
                   ) extends Spawn {

  val path: String = address.name

//  override protected val defaultExecutor: Executor = _
//  override protected val systemRef: ActorSystem = _

  // ref ! msg
  def send(to: ActorRef, msg: Any): Unit = {
    val message = ActorMessage(msg, to, this)
    to.associatedMailbox.add(message)
  }

  override def toString: String = s"ActorRef[@$path]"

}
