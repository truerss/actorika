package io.truerss.actorika

// private internal
private[actorika] case class ActorMessage(message: Any, to: ActorRef, from: ActorRef) {
  val isKill: Boolean = message match {
    case Kill => true
    case _ => false
  }
}
