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

todo:
// skip - strategy - not restart but just skip messages
// pipe pattern 
// logging ?
// context.become 
// registerOnTermination
// scheduler
// context.children
// 

### Event Stream 

```scala
import io.truerss.actorika._ 

case class Message(id: Int)

val system = ActorSystem("test")
val fooRef = system.spawn(new FooActor, "foo")

system.subscribe(fooRef, classOf[Message])

// somewhere else
system.publish(Message(1))
```


Library has lifecycles and recoverStrategies (Stop, Restart)

### note library does not support of cluster, persistence, streams and so on. 

# license: MIT 

