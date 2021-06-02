package io.truerss.actorika

import java.util.concurrent.{ConcurrentLinkedQueue => CLQ}
import scala.collection.mutable.{ArrayBuffer => AB}
import scala.reflect.runtime.universe._

// internal
private[actorika] case class RealActor(
                      actor: Actor,
                      ref: ActorRef,
                      system: ActorSystem
                    ) {

  import RealActor._
  import ActorSystem.{logger, StrategyF}

  private val subscriptions = new CLQ[Type]()

  @volatile private var inProcess = false

  private val path = ref.path

  // proxy call
  def moveStateTo(to: ActorStates.ActorState): Unit = {
    actor.moveStateTo(to)
  }

  def asUninitialized(): Unit = {
    moveStateTo(ActorStates.Uninitialized)
  }

  def asLive(): Unit = {
    moveStateTo(ActorStates.Live)
  }

  def asStopped(): Unit = {
    moveStateTo(ActorStates.Stopped)
  }

  // lock ?
  def stop(): Unit = {
    ref.associatedMailbox.clear()
    actor._children.forEach { x => actor.stop(x) }
    asStopped()
    try {
      actor.actor.postStop()
    } catch {
      case ex: Throwable =>
        logger.warn(s"Exception in 'postStop'-method in $path-actor", ex)
    }
  }

  def subscribe[T](klass: Class[T])(implicit _tag: TypeTag[T]): Unit = {
    subscriptions.add(_tag.tpe)
  }

  def canHandle[T](v: T)(implicit tag: TypeTag[T]): Boolean = {
    subscriptions.contains(tag.tpe)
  }

  def tick(): Unit = {
    actor._state match {
      case ActorStates.Live =>
        tick1()
      case ActorStates.Uninitialized =>
        // skip
      case ActorStates.Stopped =>
        Option(ref.associatedMailbox.poll()).foreach { am =>
          system._deadLettersHandler.apply(am.message, am.to, am.from)
        }
    }
  }

  private def tick1(): Unit = {
    if (!inProcess) {
      Option(ref.associatedMailbox.poll()) match {
        case Some(message) =>
          inProcess = true
          val originalSender = message.from
          val originalTo = message.to
          var receivedMessage = message
          actor.executor.execute(() => {
            var isDone = false
            var counter = 0
            var exceptionInUserDefinedFunction = false
            while(!isDone) {
              try {
                val callNow = if (counter == 0) {
                  true
                } else {
                  exceptionInUserDefinedFunction
                }
                actor.run(receivedMessage, callNow)
                exceptionInUserDefinedFunction = false
                if (receivedMessage.isKill) {
                  system.stop(ref)
                }
                isDone = true
              } catch {
                case ex: Throwable =>
                  val strategy = actor.resolveStrategy(ex, Some(receivedMessage.message), counter)

                  logger.warn(s"Exception in ${ref.path} actor, apply: $strategy-strategy", ex)

                  strategy match {
                    case ActorStrategies.Stop =>
                      // change message, the actor will be stopped with the next iteration
                      receivedMessage = ActorMessage(Kill, originalTo, originalSender)
                    case ActorStrategies.Restart =>
                      // work with system
                      tryToRestart(Vector(ex), Some(message.message))

                    case ActorStrategies.Parent =>
                      throw new IllegalStateException(illegalState)
                  }

              } finally {
                counter = counter + 1
              }
            }
            inProcess = false
          })

        case None =>
          inProcess = false
          return // mailbox is empty
      }
    }
  }

  private def illegalState: String = {
    s"Failed to resolve correct strategy: $ref"
  }

  private[actorika] def tryToRestart(stack: Vector[Throwable],
                                     originalMessage: Option[Any]): Unit = {
    asUninitialized() // stop process messages
    val result = runWhile(
      originalMessage = originalMessage,
      currentStack = stack,
      onTryBlock = () => {
        actor.preRestart()
      },
      onStopBlock = () => {
        system.stop(ref)
      },
      onRestartBlock = () => {}
    )

    if (!result.isStopCalled) {
      // do not clear world
      stop()
      tryToStart()
    }
  }

  private[actorika] def tryToStart(): Unit = {
    // do not process messages before initialization
    asUninitialized()
    runWhile(
      originalMessage = None,
      currentStack = Vector.empty,
      onTryBlock = () => {
        actor.preStart()
        asLive()
      },
      onStopBlock = () => {
        system.stop(ref)
      },
      onRestartBlock = () => {}
    )

  }

  private def runWhile(
                        originalMessage: Option[Any],
                        currentStack: Vector[Throwable],
                        onTryBlock: () => Unit,
                        onStopBlock: () => Unit,
                        onRestartBlock: () => Unit
                      ): RunWhileResult = {
    val max = system.settings.maxRestartCount
    var isDone = false
    var counter = 1
    var isStopCalled = false
    val buffer = currentStack.to(AB)
    while (!isDone) {
      try {
        onTryBlock.apply()
        isDone = true
      } catch {
        case ex: Throwable =>
          buffer += ex
          val strategy = if (counter > max) {
            logger.warn(s"Overlimit in restarts (default=$max), $path-actor will be stopped")
            ActorStrategies.Stop
          } else {
            actor.resolveStrategy(ex, originalMessage, counter)
          }
          strategy match {
            case ActorStrategies.Stop =>
              onStopBlock.apply()
              isStopCalled = true
              isDone = true

            case ActorStrategies.Restart =>
              onRestartBlock.apply()
              counter = counter + 1

            case ActorStrategies.Parent =>
              throw new IllegalStateException(illegalState)
          }
      }

    }
    RunWhileResult(isStopCalled, buffer.toVector)
  }

}

object RealActor {

  case class RunWhileResult(isStopCalled: Boolean, stack: Vector[Throwable])
  implicit class ActorExt(val actor: Actor) extends AnyVal {
    def run(actorMessage: ActorMessage, callUserFunction: Boolean): Unit = {
      actor.setSender(actorMessage.from)
      if (actor.receive.isDefinedAt(actorMessage.message)) {
        // I do not call user-receive because the function throw exception
        if (callUserFunction) {
          actor.receive.apply(actorMessage.message)
        }
      } else {
        // ignore system messages
        if (!actorMessage.isKill) {
          actor.onUnhandled(actorMessage.message)
        }
      }
    }
  }

}
