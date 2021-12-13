package com.truerss.actorika

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object ActorDsl {
  implicit class ActorRefExt(val to: ActorRef) extends AnyVal {
    def !(message: Any)(implicit from: ActorRef): Unit = {
      from.tell(message, to)
    }

    def ?(message: Any)(implicit from: ActorRef, waitTime: FiniteDuration): Future[Any] = {
      from.ask(message, to)(waitTime)
    }
  }
}
