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
// context.become 
// ask 
2 options:

ask -> push to the to-mbx
       state to Waiting
       tell after ask => push to the top of mbx
       timer start 

ask -> push to the to-mbx
       state to Waiting
       context.become to 
       message/Timeout timer
save message id ?       


// builder[T <: Actor](params...)

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

