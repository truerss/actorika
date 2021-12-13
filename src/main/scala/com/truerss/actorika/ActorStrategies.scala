package com.truerss.actorika

object ActorStrategies extends Enumeration {
  type ActorStrategy = Value
  val Stop, Restart, Skip = Value
}