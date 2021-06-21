package io.truerss.actorika

import java.util.concurrent.Executor

case class ActorSystemSettings(
                                handleDeadLetters: Boolean,
                                maxRestartCount: Int,
                                defaultExecutor: Executor,
                                exceptionOnStart: Boolean
                        )

object ActorSystemSettings {
  val default: ActorSystemSettings = new ActorSystemSettings(
    handleDeadLetters = true,
    maxRestartCount = 100,
    defaultExecutor = null,
    exceptionOnStart = false
  )

}

