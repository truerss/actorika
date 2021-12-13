package com.truerss.actorika

import java.util.concurrent.{ConcurrentLinkedQueue, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class ActorHierarchyTests extends Test {

  import scala.jdk.CollectionConverters._

  private val childrenCounter = new AtomicInteger(0)
  private val stopped = new ConcurrentLinkedQueue[String]()

  private case class Allocate(name: String)
  private case object StopMe

  private class RootActor extends Actor {
    import ActorDsl._
    def receive: Receive = {
      case Allocate(name) =>
        val ref = spawn(new FirstChild, name)
        sender ! ref
      case StopMe =>
        stop()
    }
  }

  private class FirstChild extends Actor {
    import ActorDsl._
    def receive: Receive = {
      case Allocate(name) =>
        sender ! spawn(new SecondChild, name)
      case StopMe =>
        stop()
    }
  }

  private class SecondChild extends Actor {

    override def preStart(): Unit = {
      childrenCounter.incrementAndGet()
    }

    override def postStop(): Unit = {
      stopped.add(me.address.path)
    }

    def receive: Receive = {
      case StopMe =>
        stop()
    }
  }

  /**
   * Create actor hierarchy
   * then stop FirstChild -> SecondChild also should stopped
   * then Stop one of the SecondChildren -> Parent should be present in the hierarchy
   * then Stop TestRef -> `world` should be empty
   */
  test("should stop children on actor Stop") {
    val system = ActorSystem("actor-system")
    implicit val ec = scala.concurrent.ExecutionContext.global

    implicit val time: FiniteDuration = FiniteDuration(1, TimeUnit.SECONDS)
    system.start()
    val testRef = system.spawn(new RootActor, "test")

    val f0 = Await.result(
      Future.sequence {
        (0 to 3).map { x =>
          system.ask(Allocate(s"ch-$x"), testRef).map { x => x.asInstanceOf[ActorRef] }
        }
      },
      10.seconds
    )

    val ch0Ref = f0.find(x => x.address.path.endsWith("ch-0")).get
    val ch1Ref = f0.find(x => x.address.path.endsWith("ch-1")).get
    val children = Await.result(
      Future.sequence {
        f0.map { chRef =>
          val num = """\d+""".r.findAllIn(chRef.address.path).toList.last
          system.ask(Allocate(s"second-$num"), chRef).map { x =>
            x.asInstanceOf[ActorRef]
          }
        }
      },
      10.seconds
    )

    val ch0Actor = system.findRealActor(system.systemActor, ch0Ref.address.path)
    assert1(ch0Actor.map(_._state).contains(ActorStates.Live))
    val ch1Actor = system.findRealActor(system.systemActor, ch1Ref.address.path)
    assert1(ch1Actor.map(_._state).contains(ActorStates.Live))

    println(s"stop actor $ch0Ref with system.stop method")
    system.stop(ch0Ref)
    println(s"stop actor $ch1Ref actor with message")
    testRef.tell(StopMe, ch1Ref)

    val s3 = children.find(x => x.address.path.endsWith("second-3")).get
    val s3Actor = system.findRealActor(system.systemActor, s3.address.path)
    assert1(s3Actor.map(_._state).contains(ActorStates.Live))
    println(s"stop second-child-actor: $s3")
    testRef.tell(StopMe, s3)
    assert1(system.find("ch-0").isEmpty)
    assert1(system.find("ch-1").isEmpty)
    assert1(system.find("second-0").isEmpty)
    assert1(system.find("second-1").isEmpty)
    assert1(system.find("second-3").isEmpty)
    assert1(ch0Actor.get._state == ActorStates.Finished)
    assert1(ch1Actor.get._state == ActorStates.Finished)
    assert1(s3Actor.get._state == ActorStates.Finished)
    assert1(stopped.asScala.toVector.map { x =>
      x.split("/").last
    }.sorted == Vector("second-0", "second-1", "second-3").sorted)
    assert1(childrenCounter.get() == children.size)
    system.stop()
    assert1(system.isStopped)
  }


}
