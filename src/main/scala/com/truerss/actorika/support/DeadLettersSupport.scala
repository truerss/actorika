package com.truerss.actorika.support

import com.truerss.actorika.{ActorRef, DeadLettersHandler, DefaultDeadLettersHandler}

private [actorika] trait DeadLettersSupport {
  private [actorika] var _deadLettersHandler: DeadLettersHandler =
    new DefaultDeadLettersHandler

  def registerDeadLetterHandler(handler: (Any, ActorRef, ActorRef) => Unit): Unit = {
    val tmp = new DeadLettersHandler {
      override def handle(message: Any, from: ActorRef, to: ActorRef): Unit = {
        handler.apply(message, from, to)
      }
    }
    _deadLettersHandler = tmp
  }

  def registerDeadLetterHandler(handler: DeadLettersHandler): Unit = {
    _deadLettersHandler = handler
  }
}
