package io.truerss.actorika

object ActorStates extends Enumeration {
  type ActorState = Value
  val Uninitialized, Live, Stopped = Value
}
