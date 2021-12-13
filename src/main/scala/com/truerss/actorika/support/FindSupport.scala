package com.truerss.actorika.support

import com.truerss.actorika.{Actor, ActorRef, ActorSystem}

import scala.jdk.CollectionConverters._

private [actorika] trait FindSupport { self: ActorSystem =>

  def find(path: String): Option[ActorRef] = {
    findActor(path).map(_.me)
  }

  private [actorika] def findActor(path: String): Option[Actor] = {
    systemActor._children.asScala.find { case (p, _) => p.is(path) } match {
      case Some((_, ra)) =>
        Some(ra)
      case None =>
        systemActor._children.values().asScala.to(LazyList)
          .map { x =>
            findRealActor(x, path)
          }.collectFirst {
          case Some(r) =>
            r
        }
    }
  }

  private [actorika] def findRealActor(ra: Actor, path: String): Option[Actor] = {
    if (ra.me.address.is(path)) {
      Some(ra)
    } else {
      val chs = ra._children
      chs.asScala.find { case (p, _) =>
        p.is(path)
      } match {
        case Some((_, ra)) => Some(ra)
        case None =>
          chs.values().asScala.to(LazyList)
            .map { x => findRealActor(x, path) }
            .collectFirst {
              case Some(r) => r
            }
      }
    }
  }

}
