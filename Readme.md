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

### Event Stream 

```scala
import io.truerss.actorika._ 

case class Message(id: Int)

val fooRef = system.spawn(new FooActor, "foo")
val system = ActorSystem("test")

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

### Scheduler

```scala
class MyActor extends Actor {
  override def preStart(): Unit = {
    scheduler.once(1.second){ () =>
      println("once")
    }
    scheduler.every(1.second) { () =>
      println("every")
    }
  }
}  
```

### Lifecycle

```scala
class MyActor extends Actor {
  
  override def preStart(): Unit = { }
  override def postStop(): Unit = { }
  override def preRestart(): Unit = { }
  
  def receive: Receive = {
    case _ =>
  }
}

```

### Strategies Description

```
Parent -> will apply Parent strategy (by default)
Stop   -> actor will be stopped
Skip   -> message will be skipped
Restart -> preRestart(), clear mailbox, stop Actor, call postStop, then start Actor again (preStart)   
```

### note library does not support of cluster, persistence, streams and so on. 

# license: MIT 

