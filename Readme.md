# actor implementation 

light drop-in replacement of Akka

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

### Change Receive function: become

```scala

case object Change

class MyActor extends Actor {
  def receive: Receive = {
    case Change =>
      become(anotherReceive)   
  }

  def anotherReceive: Receive = {
    case _ =>     
  }
       
}

```

### Custom Actor Names:

```scala
import io.truerss.actorika.ActorNameGenerator
val generator = ActorNameGenerator("custom")

system.spawn(new MyActor, generator)
```


Library has lifecycles and recoverStrategies (Stop, Restart, Parent, Skip)

### note library does not support of cluster, persistence, streams and so on. 

# license: MIT 

