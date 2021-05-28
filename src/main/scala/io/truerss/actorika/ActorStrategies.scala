package io.truerss.actorika

object ActorStrategies extends Enumeration {
  type ActorStrategy = Value
  // todo add Retry (message will pass again and again)
  val Stop, Restart = Value
}

