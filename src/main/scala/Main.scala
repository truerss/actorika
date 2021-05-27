import io.truerss.actorika._

class BarActor extends Actor {
  import Actor._
  override def receive: Receive = {
    case msg =>
      println(s"bar---> $msg & $sender")
      sender ! "pong"
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
      println(s"foo----> $msg & ${sender}")
      sender ! "ping"
    case msg =>
      throw new Exception("boom")
      println(s"--------> $msg & ${sender}")
  }

  override def postStop(): Unit = {
    println(s"---------> ${me} stopped")
  }
}


object Main extends App {

  val system = ActorSystem("test")

  val bar = system.spawn(new BarActor, "bar")
  val foo = system.spawn(new FooActor(bar), "foo")
//  system.send(foo, StartGame)
  bar.send(foo, "asd")
//  foo.send(foo, "qwe")

  system.run()

}
