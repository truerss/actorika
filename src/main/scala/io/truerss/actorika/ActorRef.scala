package io.truerss.actorika

import java.util.concurrent.{ConcurrentLinkedQueue => CLQ}
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.FiniteDuration

case class ActorRef(
                     address: Address,
                     private[actorika] val isSystemRef: Boolean,
                     private[actorika] val associatedMailbox: CLQ[ActorMessage]
                   ) {

  val path: String = address.name

  def send(to: ActorRef, msg: Any): Unit = {
    val message = ActorTellMessage(msg, to, this)
    push(to, message)
  }

  def ask(to: ActorRef, msg: Any)(implicit waitTime: FiniteDuration): Future[Any] = {
    val p = Promise[Any]()
    val message = ActorAskMessage(msg, to, this, waitTime, p)
    push(to, message)
    p.future
  }

  private def push(to: ActorRef, message: ActorMessage): Unit = {
    if (to.isSystemRef) {
      throw new IllegalArgumentException(
        s"You're trying to send ${message.message} to the system-mailbox, it's not possible"
      )
    } else {
      to.associatedMailbox.add(message)
    }
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