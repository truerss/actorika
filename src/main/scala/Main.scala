import io.truerss.actorika._

import scala.reflect.runtime.universe

class BarActor extends Actor {
  import Actor._
  override def receive: Receive = {
    case StartGame =>
      println("received!!!!!!!!!!!!!")
    case msg =>
      println(s"bar---> $msg & $sender & ${Thread.currentThread().getName}")
      sender ! "pong"
  }
}

class TestActor extends Actor {
  def receive = {
    case msg =>
      println(s"========> ${msg} & $me $sender")

  }
}

class FooActor(barRef: ActorRef) extends Actor {
  import Actor._

  override def receive: Receive = {
    case _: StartGame.type =>
      barRef ! "ping"
      barRef ! "ping"
      barRef ! "ping"
    case msg: String if msg == "pong" =>
      println(s"foo----> $msg & ${sender} & ${Thread.currentThread().getName}")
      sender ! "ping"
    case msg =>
      val ref = spawn(new TestActor, "asd")
      println(s"~> ${ref}")
      ref ! "1ewq"
      println(s"--------> $msg & ${sender}")
  }

  override def postStop(): Unit = {
    println(s"---------> ${me} stopped")
  }
}

object StartGame

object Main extends App {

  val system = ActorSystem("test")

  val bar = system.spawn(new BarActor, "bar")
  val foo = system.spawn(new FooActor(bar), "foo")

  system.subscribe(bar, classOf[StartGame.type])
  system.publish(StartGame)

  system.run()
//  system.send(foo, StartGame)
//  bar.send(foo, "asd")
//  system.send(bar, "asd")
//  system.subscribe(foo, classOf[String])
//  foo.send(foo, "qwe")


//  system.run()

}
