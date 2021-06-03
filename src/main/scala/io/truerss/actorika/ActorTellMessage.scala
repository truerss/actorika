package io.truerss.actorika

import scala.concurrent.duration.FiniteDuration

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
                                             timeout: FiniteDuration
                                            )
  extends ActorMessage(message, to, from)