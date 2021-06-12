package io.truerss.actorika

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class AskTests extends munit.FunSuite {

  private val tmp = new AtomicInteger(0)
  @volatile private var exceptionRaised = ""
  @volatile private var flag = false

  private class FooActor(barRef: ActorRef) extends Actor {

    def receive = {
      case x: Int =>
        implicit val ec = ExecutionContext.fromExecutor(executor)
        val result = me.ask(barRef, x)(1.second)
        result.onComplete {
          case Success(value) =>
            tmp.set(value.asInstanceOf[Int])

          case Failure(AskException(message)) =>
            exceptionRaised = message
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

  test("ask".flaky) {
    val system = ActorSystem("system")
    var msg = ""
    val handler = new DeadLettersHandler {
      override def handle(message: Any, to: ActorRef, from: ActorRef): Unit = {
        msg = s"$message:${to.path}:${from.path}"
      }
    }
    system.registerDeadLetterHandler(handler)
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
    assert(exceptionRaised.isEmpty)
    Thread.sleep(100)
    flag = true
    system.send(foo, 100)
    Thread.sleep(1500)
    assert(tmp.get() == 10)
    assert(exceptionRaised.contains("Timeout 1 second is over on 100 message"))
    assert(msg == s"1:system/anon-ask-2:system/bar")
    system.stop()
  }


}
