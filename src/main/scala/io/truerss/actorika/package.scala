package io.truerss

package object actorika {
  object Kill
  object AskTimeout

  private [actorika] object StartAsk

  case class AskException(message: String) extends Exception

  case class ActorInitializationError(message: String) extends Exception
}
