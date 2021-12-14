package io.truerss.actorika

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

/**
 * @param handleDeadLetters
 * @param maxRestartCount
 * @param defaultExecutor    - main executor, where all actors wait for new messages
 * @param exceptionOnStart
 * @param defaultStrategy    - default is `ActorStrategies.Skip`
 * @param defaultMailboxSize
 * @param defaultWaitTime    - sleep time between check if actor is ready to execute the next message, default is 100 millis
 */
case class ActorSetup(
                       handleDeadLetters: Boolean,
                       maxRestartCount: Int,
                       defaultExecutor: ExecutionContext,
                       exceptionOnStart: Boolean,
                       defaultStrategy: ActorStrategies.Value,
                       defaultMailboxSize: Int,
                       defaultWaitTime: FiniteDuration
                     )

object ActorSetup {
  val default: ActorSetup = new ActorSetup(
    handleDeadLetters = true,
    maxRestartCount = 100,
    defaultExecutor = ExecutionContext.fromExecutor(Executors.newCachedThreadPool(
      ActorSystem.threadFactory("default-executor")
    )),
    exceptionOnStart = false,
    defaultStrategy = ActorStrategies.Skip,
    defaultMailboxSize = 100,
    defaultWaitTime = 100.millis
  )
}
