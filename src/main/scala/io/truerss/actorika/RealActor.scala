package io.truerss.actorika

import java.util.concurrent.{ConcurrentLinkedQueue => CLQ}
import scala.collection.mutable.{ArrayBuffer => AB}
import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration
import scala.reflect.runtime.universe._

// internal
private[actorika] case class RealActor(
                      actor: Actor,
                      ref: ActorRef,
                      system: ActorSystem
                    ) {

  import RealActor._
  import ActorSystem.logger

  private[actorika] val subscriptions = new CLQ[Type]()

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

  def stop(): Unit = {
    asStopped()
    ref.associatedMailbox.clear()
    actor._children.forEach { (_, ch) => ch.stop() }
    try {
      actor.postStop()
    } catch {
      case ex: Throwable =>
        logger.warn(s"Exception in 'postStop'-method in $path-actor", ex)
    }
    system.resolveParent(ref) match {
      case Some(parent) =>
        parent.stopMe(ref)
      case None =>
        logger.warn(s"Can not detect parent of $ref")
    }
  }

  def stopMe(ref: ActorRef): Unit = {
    actor._children.remove(ref.path)
  }

  def subscribe[T](klass: Class[T])(implicit _tag: TypeTag[T]): Unit = {
    subscriptions.add(_tag.tpe)
  }

  def unsubscribe[T](klass: Class[T])(implicit _tag: TypeTag[T]): Unit = {
    subscriptions.remove(_tag.tpe)
  }

  def unsubscribe(): Unit = {
    subscriptions.clear()
  }

  def canHandle[T](v: T)(implicit tag: TypeTag[T]): Boolean = {
    subscriptions.contains(tag.tpe)
  }

  def tick(): Unit = {
    actor._state match {
      case ActorStates.Live =>
        tick1()
        // and for children too
        actor._children.forEach { (_, x) => x.tick() }
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
                run(receivedMessage, callNow)
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
                      receivedMessage = ActorTellMessage(Kill, originalTo, originalSender)
                    case ActorStrategies.Restart =>
                      // work with system
                      tryToRestart(Vector(ex), Some(message.message))

                    case ActorStrategies.Skip =>
                      // no need to handle this message, just skip
                      isDone = true

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

  def run(actorMessage: ActorMessage, callUserFunction: Boolean): Unit = {
    actor.setSender(actorMessage.from)
    val handler = actor.currentHandler
    if (handler.isDefinedAt(actorMessage.message)) {
      // I do not call user-receive because the function will throw the exception
      if (callUserFunction) {
        actorMessage match {
          case ActorAskMessage(message, to, from, timeout, promise) =>
            val anon = startAskActor(message, timeout, promise)
            val ref = system.spawn(anon, ActorNameGenerator.ask)
            from.send(ref, StartAsk)
            // apply then
            ref.send(to, message)

          case _ =>
            handler.apply(actorMessage.message)
        }
      }
    } else {
      // ignore system messages
      if (!actorMessage.isKill) {
        actor.onUnhandled(actorMessage.message)
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
      // I do not clear the world
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

            case ActorStrategies.Restart | ActorStrategies.Skip =>
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

  private def startAskActor(message: Any, timeout: FiniteDuration, promise: Promise[Any]): Actor = {
    new Actor {
      var sch: SchedulerTask = null
      override def receive: Receive = {
        case StartAsk =>
          sch = system.scheduler.once(timeout) { () =>
            me.send(me, AskTimeout)
          }

        case AskTimeout =>
          promise.failure(AskException(s"Timeout $timeout is over on $message message"))
          stop()
        case msg: Any =>
          sch.clear()
          promise.success(msg)
          stop()
      }
    }
  }


}
