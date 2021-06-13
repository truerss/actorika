package io.truerss.actorika

import java.util.concurrent.{ConcurrentLinkedQueue, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.FiniteDuration

class ActorHierarchyTests extends munit.FunSuite {

  import scala.jdk.CollectionConverters._

  private val childrenCounter = new AtomicInteger(0)
  private val stopped = new ConcurrentLinkedQueue[String]()

  private case class Allocate(name: String)
  private case object StopMe

  private class TestActor extends Actor {
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
      stopped.add(me.path)
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
    val system = ActorSystem("system")
    import system.context
    import scala.concurrent.duration._
    implicit val time = FiniteDuration(100, TimeUnit.MILLISECONDS)
    system.start()
    val testRef = system.spawn(new TestActor, "test")

    val f0 = Await.result(
      Future.sequence {
        (0 to 3).map { x =>
          system.ask(testRef, Allocate(s"ch-$x")).map { x => x.asInstanceOf[ActorRef] }
        }
      },
      1.second
    )
    val ch0Ref = f0.find(_.path.endsWith("ch-0")).get
    val ch1Ref = f0.find(_.path.endsWith("ch-1")).get
    val children = Await.result(
      Future.sequence {
        f0.map { chRef =>
          val num = """\d+""".r.findAllIn(chRef.path).toList.last
          system.ask(chRef, Allocate(s"second-$num")).map { x =>
            x.asInstanceOf[ActorRef]
          }
        }
      },
      1.second
    )

    println(s"stop actor $ch0Ref with system.stop method")
    system.stop(ch0Ref)
    println(s"stop actor $ch1Ref actor with message")
    testRef.send(ch1Ref, StopMe)
    val s3 = children.find(x => x.path.endsWith("second-3")).get
    println(s"stop second-child-actor: $s3")
    testRef.send(s3, StopMe)
    Thread.sleep(100)
    assert(system.find("ch-0").isEmpty)
    assert(system.find("ch-1").isEmpty)
    assert(system.find("second-0").isEmpty)
    assert(system.find("second-1").isEmpty)
    assert(system.find("second-3").isEmpty)
    assert(stopped.asScala.toVector.map{ x =>
      x.split("/").last
    }.sorted == Vector("second-0", "second-1", "second-3").sorted)
    assert(childrenCounter.get() == children.size)
    Thread.sleep(100)
    system.stop()
    Thread.sleep(100)
    assert(system.world.isEmpty)
  }


}
