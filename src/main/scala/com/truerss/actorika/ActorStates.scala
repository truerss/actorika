package com.truerss.actorika

object ActorStates extends Enumeration {
  type ActorState = Value
  val UnInitialized, Live, Finished = Value
}
