package io.truerss.actorika.support

import io.truerss.actorika.{Actor, ActorRef, ActorSystem}

import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

private [actorika] trait SubscriptionSupport { self: ActorSystem =>

  private [actorika] def findMe(ref: ActorRef): Option[Actor] = {
    if (ref.isSystemRoot) {
      Some(self.systemActor)
    } else {
      Option(systemActor._children.get(ref.address)) match {
        case Some(ra) =>
          Some(ra)
        case None =>
          systemActor._children.values().asScala.to(LazyList)
            .map { actor =>
              findMe(actor, ref)
            }.collectFirst {
            case Some(r) => r
          }
      }
    }
  }

  private def findMe(ra: Actor, ref: ActorRef): Option[Actor] = {
    if (ra.me.address == ref.address) {
      Some(ra)
    } else {
      val chs = ra._children
      if (chs.contains(ref.address)) {
        Option(chs.get(ref.address))   // find in children
      } else {
        // ok try to find deeper
        val r =  chs.values().asScala.to(LazyList)
          .map { x => findMe(x, ref) }
          .collectFirst {
            case Some(r) => r
          }
        r
      }
    }
  }

  def subscribe[T](ref: ActorRef, klass: Class[T]): Unit = {
    findMe(ref).foreach { actor =>
      actor.subscribe(klass)
    }
  }

  def unsubscribe[T](ref: ActorRef, klass: Class[T]): Unit = {
    findMe(ref).foreach { actor =>
      actor.unsubscribe(klass)
    }
  }

  def unsubscribe(ref: ActorRef): Unit = {
    findMe(ref).foreach { actor =>
      actor.unsubscribe()
    }
  }

  def publish[T](message: T)(implicit kTag: ClassTag[T]): Unit = {
    val handlers = systemActor._children.values().asScala.to(LazyList)
      .map { x =>
        canHandle(x, message)
      }.collect {
      case xs if xs.nonEmpty => xs
    }.flatten

    if (handlers.isEmpty) {
      ActorSystem.logger.warn(s"Can not publish: ${message.getClass}, there are no actors to handle the message")
      _deadLettersHandler.handle(message, systemActor.me, systemActor.me)
    } else {
      handlers.foreach { h =>
        systemActor._context.me.tell(message, h._context.me)
      }
    }
  }

  private def canHandle[T](actor: Actor, message: T)(implicit kTag: ClassTag[T]): LazyList[Actor] = {
    val root = if (actor.canHandle(message)(kTag)) {
      Some(actor)
    } else {
      None
    }
    val inChs = actor._children.asScala.values.to(LazyList)
      .map { x => canHandle(x, message)(kTag) }
      .collect {
        case xs if xs.nonEmpty => xs
      }
    root.map { x => inChs.flatten :+ x }.getOrElse(inChs.flatten)
  }
}
