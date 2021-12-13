package com.truerss.actorika

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ConcurrentLinkedQueue => CLQ}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.jdk.CollectionConverters
import scala.util.{Failure, Success}

class AskTests extends Test {

  import CollectionConverters._

  private val tmp = new CLQ[Int]
  private val exceptionRaised = new AtomicReference[String]("")
  @volatile private var flag = false

  private class FooActor(barRef: ActorRef) extends Actor {
    override def receive: Receive = {
      case x: Int =>
        println(s"fooActor ----> $x ${Thread.currentThread().getName}")
        implicit val ec = ExecutionContext.fromExecutor(executor)
        val result = me.ask(x, barRef)(1.second)
        result.onComplete {
          case Success(value: Int) =>
            tmp.add(value)
          case Success(_) =>
            println("interesting...")
          case Failure(AskException(message)) =>
            exceptionRaised.set(message)
          case Failure(_) =>
            println("interesting...")
        }
    }
  }

  private class BarActor extends Actor {

    override def applyStrategy(ex: Throwable, failedMessage: Option[Any], count: Int): ActorStrategies.Value = {
      ActorStrategies.Skip
    }

    override def receive: Receive = {
      case x: Int =>
        println(s"barActor $x and $flag and ${Thread.currentThread().getName}")
        if (flag) {
          Thread.sleep(1200)
        }
        me.tell(100/x, sender)
    }
  }

  test("ask") {
    val system = ActorSystem("ask-test-system")
    system.start()
    val bar = system.spawn(new BarActor, "bar")
    val foo = system.spawn(new FooActor(bar), "foo")
    system.tell(10, foo)
    Thread.sleep(600)
    assert1(tmp.asScala.head == 10, s"tmp=${tmp} and ${Thread.currentThread().getName}")

    // if exception
    system.tell(0, foo)
    Thread.sleep(1000)
    assert1(tmp.asScala.head == 10, s"tmp=${tmp}")
    Thread.sleep(100)
    flag = true
    system.tell(100, foo)
    Thread.sleep(1500)
    assert1(exceptionRaised.get().contains("Timeout 1 second is over on 100 message"), s"message: ${exceptionRaised.get()}")
    system.stop()
  }


}
