package io.truerss.actorika

trait DeadLettersHandler {
  def handle(message: Any, to: ActorRef, from: ActorRef): Unit
}

private class DefaultDeadLettersHandler extends DeadLettersHandler {
  override def handle(message: Any, to: ActorRef, from: ActorRef): Unit = {
    ActorSystem.logger.warn(s"DeadLetter detected: $message, from:$from, to:$to")
  }
}