package io.truerss.actorika

import java.util.concurrent.{Executor, Executors, ThreadFactory, ConcurrentLinkedQueue => CLQ}
import java.util.{ArrayList => AL}

case class ActorSystem(systemName: String) extends Spawn {

  override protected val address: Address = Address(systemName)

  private class ActorikaThreadFactory extends ThreadFactory {
    override def newThread(r: Runnable): Thread = {
      new Thread(r, systemName)
    }
  }

  private val cores = Runtime.getRuntime.availableProcessors()

  override protected val defaultExecutor: Executor = Executors.newFixedThreadPool(cores,
    new ActorikaThreadFactory()
  )

  override protected val systemRef: ActorSystem = this
  // no messages for processing
  private val system: ActorRef = ActorRef(address, new CLQ[ActorMessage](
    new AL[ActorMessage](0)),
    defaultExecutor,
    this
  )

  // def registerDeadLetterChannel: Received => From, Option(to)

  // todo in separate thread
  def run(): Unit = {
    // tick-tack event loop
    while (true) {
      world.forEach { (_, actor) =>
        actor.tick()
      }
    }
  }

  def send(to: ActorRef, msg: Any): Unit = {
    val message = ActorMessage(msg, to, system)
    to.associatedMailbox.add(message)
  }

  override def toString: String = s"ActorSystem($systemName)"

}

