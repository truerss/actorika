package io.truerss.actorika

import java.util.concurrent.{ArrayBlockingQueue => ABQ}
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.FiniteDuration

// ref ! ref -> mailbox -> actor
case class ActorRef(
                   address: Address,
                   private[actorika] val mailBox: MailBox,
                   private[actorika] val system: ActorSystem,
                   private[actorika] val isSystemRoot: Boolean = false
                   ) {
  def tell(message: Any, to: ActorRef): Unit = {
    val tmp = ActorTellMessage(
      message = message,
      from = this,
      to = to
    )
    to.mailBox.push(tmp)
    system.tick(to)
  }

  def ask(message: Any, to: ActorRef)(implicit waitTime: FiniteDuration): Future[Any] = {
    val p = Promise[Any]()
    val tmp = ActorAskMessage(message, to, this, waitTime, p)
    to.mailBox.push(tmp)
    system.tick(to)
    p.future
  }

  def path: String = address.path

  override def toString: String = {
    s"ActorRef(${address.path})"
  }
}

object ActorRef {
  def empty(system: ActorSystem) = new ActorRef(Address("/"), MailBox(1), system)
}


case class Address(path: String) {
  def is(sub: String): Boolean = {
    path == sub
  }
}

object Address {
  private [actorika] def apply(parent: Actor, name: String): Address = {
    new Address(s"${parent._context.address.path}/$name")
  }
}


case class MailBox(capacity: Int) {
  require(capacity > 0)
  val queue: ABQ[ActorMessage] = new ABQ[ActorMessage](capacity)

  def push(message: ActorMessage): Unit = {
    queue.put(message)
  }

  def isEmpty: Boolean = queue.isEmpty

  def size: Int = queue.size()

  def clear: Unit = queue.clear()
}

private[actorika] abstract class ActorMessage(
                                               val message: Any,
                                               val to: ActorRef,
                                               val from: ActorRef) {
  val isKill: Boolean = message match {
    case Kill => true
    case _ => false
  }
}

private[actorika] case class ActorTellMessage(override val message: Any,
                                              override val to: ActorRef,
                                              override val from: ActorRef)
  extends ActorMessage(message, to, from)

private[actorika] case class ActorAskMessage(override val message: Any,
                                             override val to: ActorRef,
                                             override val from: ActorRef,
                                             timeout: FiniteDuration,
                                             promiseToResolve: Promise[Any]
                                            )
  extends ActorMessage(message, to, from)