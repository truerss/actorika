package io.truerss

package object actorika {
  object Kill
  object AskTimeout

  private[actorika] object StartAsk  // rm after builder impl

  case class AskException(message: String) extends Exception

}
