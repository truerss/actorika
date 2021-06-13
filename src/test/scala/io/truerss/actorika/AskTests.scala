package io.truerss.actorika

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class AskTests extends munit.FunSuite {

  private val tmp = new AtomicInteger(0)
  private val exceptionRaised = new AtomicReference[String]("")
  @volatile private var flag = false

  private class FooActor(barRef: ActorRef) extends Actor {

    def receive: Receive = {
      case x: Int =>
        implicit val ec = ExecutionContext.fromExecutor(executor)
        val result = me.ask(barRef, x)(1.second)
        result.onComplete {
          case Success(value) =>
            tmp.set(value.asInstanceOf[Int])

          case Failure(AskException(message)) =>
            exceptionRaised.set(message)
          case Failure(_) =>
            println("interesting...")
        }
    }
  }

  private class BarActor extends Actor {

    override def applyRestartStrategy(ex: Throwable, failedMessage: Option[Any], count: Int): ActorStrategies.Value = {
      ActorStrategies.Skip
    }

    def receive: Receive = {
      case x: Int =>
        if (flag) {
          Thread.sleep(1200)
        }
        me.send(sender, 100/x)
    }
  }

  test("ask") {
    val system = ActorSystem("system")
    val bar = system.spawn(new BarActor, "bar")
    val foo = system.spawn(new FooActor(bar), "foo")
    system.start()
    system.send(foo, 10)
    Thread.sleep(1000)
    assert(tmp.get() == 10)

    // if exception
    system.send(foo, 0)
    Thread.sleep(1000)
    assert(tmp.get() == 10)
    Thread.sleep(100)
    flag = true
    system.send(foo, 100)
    Thread.sleep(1500)
    assert(tmp.get() == 10)
    assert(exceptionRaised.get().contains("Timeout 1 second is over on 100 message"))
    system.stop()
  }


}
