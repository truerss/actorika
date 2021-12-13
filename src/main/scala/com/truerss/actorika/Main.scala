package com.truerss.actorika

import java.util.concurrent.Executors

object Main extends App {

  val a = Executors.newFixedThreadPool(2)

  class Actor1 extends Actor {
    override def receive: Receive = {
      case x =>
        println(s"handle: $x")
    }
  }

  class Actor2 extends Actor {
    override def receive: Receive = {
      case x =>
        println(s"===================>: $x")
    }
  }

  val system = ActorSystem("test-system")
  system.start()
  val ref2 = system.spawn(new Actor2, "actor2")
  ref2.tell("deadlock", ref2)
  val ref = system.spawn(new Actor1, "actor1")

  (0 to 10).foreach { x =>
    ref.tell(s"asdasd-${x}", ref)
  }


  println(1)
  Thread.sleep(3000)
  println(2)
  system.stop()
  println(3)

}
