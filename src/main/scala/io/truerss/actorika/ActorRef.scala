package io.truerss.actorika

import java.util.concurrent.{ConcurrentLinkedQueue => CLQ}

case class ActorRef(
                     address: Address,
                     private[actorika] val associatedMailbox: CLQ[ActorMessage]
                   ) {

  val path: String = address.name

  // ref ! msg
  def send(to: ActorRef, msg: Any): Unit = {
    val message = ActorMessage(msg, to, this)
    to.associatedMailbox.add(message)
  }

  override def toString: String = s"ActorRef[@$path]"

}
