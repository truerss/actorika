# actor implementation 

light drop-in replacement of akka

```scala
import io.truerss.actorika._ 

class FooActor extends Actor {

  def receive: Receive = {
    case msg => 
                
  }
}

val system = ActorSystem("test")
val fooRef = system.spawn(new FooActor, "foo")
system.send(fooRef, "test")


```

Library has lifecycles and recoverStrategies (Stop, Restart)

### note library does not support of cluster, persistence, streams and so on. 

# license: MIT 

