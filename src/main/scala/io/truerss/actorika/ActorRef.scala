package io.truerss.actorika

import java.util.concurrent.{ConcurrentLinkedQueue => CLQ}

case class ActorRef(
                     address: Address,
                     private[actorika] val isSystemRef: Boolean,
                     private[actorika] val associatedMailbox: CLQ[ActorMessage]
                   ) {

  val path: String = address.name

  def send(to: ActorRef, msg: Any): Unit = {
    val message = ActorMessage(msg, to, this)
    to.associatedMailbox.add(message)
  }

  private[actorika] def hasMessages: Boolean = {
    !associatedMailbox.isEmpty
  }

  override def toString: String = s"ActorRef[@$path]"

}

object ActorRef {
  def apply(address: Address, associatedMailbox: CLQ[ActorMessage]): ActorRef = {
    new ActorRef(address, isSystemRef = false, associatedMailbox)
  }

  def apply(address: Address): ActorRef = {
    new ActorRef(address, isSystemRef = false, new CLQ[ActorMessage]())
  }
}