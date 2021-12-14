package io.truerss.actorika

import java.util.concurrent.atomic.AtomicLong

abstract class ActorNameGenerator(val pattern: String) {
  def next(): String
}

object ActorNameGenerator {
  def create(pattern: String): ActorNameGenerator = new ActorNameGenerator(pattern) {
    private final val defaultNameCounter = new AtomicLong(0)

    override def next(): String = {
      s"$pattern-${defaultNameCounter.getAndIncrement()}"
    }
  }

  private [actorika] val default: ActorNameGenerator = create("actor")
}
