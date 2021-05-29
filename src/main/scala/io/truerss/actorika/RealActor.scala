package io.truerss.actorika

import java.util.concurrent.{ConcurrentLinkedQueue => CLQ}
import scala.reflect.runtime.universe._

// internal
private[actorika] case class RealActor(
                     actor: Actor,
                     ref: ActorRef,
                     systemRef: ActorSystem
                    ) {

  import RealActor._

  private val subscriptions = new CLQ[Type]()

  @volatile private var inProcess = false

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
    asStopped()
    try {
      actor.actor.postStop()
    } catch {
      case _: Throwable =>
        // log
    }
  }

  def subscribe[T](klass: Class[T])(implicit _tag: TypeTag[T]): Unit = {
    subscriptions.add(_tag.tpe)
  }

  def canHandle[T](v: T)(implicit tag: TypeTag[T]): Boolean = {
    // lst.exists(_ <:< tag.tpe)
    subscriptions.contains(tag.tpe)
  }

  def tick(): Unit = {
    actor.state match {
      case ActorStates.Live =>
        tick1()
      case ActorStates.Uninitialized =>
        // skip
      case ActorStates.Stopped =>
        // to system deadletters todo
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
                  systemRef.stop(ref)
                }
                isDone = true
                inProcess = false
              } catch {
                case ex: Throwable =>
                  actor.applyRestartStrategy(ex, Some(receivedMessage), counter) match {
                    case ActorStrategies.Stop =>
                      // change message, the actor will be stopped with next iteration
                      receivedMessage = ActorMessage(Kill, originalTo, originalSender)
                    case ActorStrategies.Restart =>
                      // work with system
                      systemRef.restart(this)
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

}

object RealActor {
  private final val empty = () => ()

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
