package com.truerss.actorika.support

import com.truerss.actorika.{ActorRef, ActorSystem}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

private [actorika] trait LikeAnActor { self: ActorSystem =>

  def tell(message: Any, to: ActorRef): Unit = {
    systemActor._context.me.tell(message, to)
  }

  def ask(message: Any, to: ActorRef)(implicit waitTime: FiniteDuration): Future[Any] = {
    systemActor._context.me.ask(message, to)(waitTime)
  }
}
