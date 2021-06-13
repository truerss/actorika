package io.truerss.actorika

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object ActorDsl {
  implicit class ActorRefExt(val to: ActorRef) extends AnyVal {
    def !(msg: Any)(implicit from: ActorRef): Unit = {
      from.send(to, msg)
    }

    def ?(msg: Any)(implicit from: ActorRef, waitTime: FiniteDuration): Future[Any] = {
      from.ask(to, msg)(waitTime)
    }
  }
}
