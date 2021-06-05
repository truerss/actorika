package io.truerss.actorika

case class ActorNameGenerator(pattern: String) {
  def next(inc: Long): String = s"$pattern-$inc"
}

object ActorNameGenerator {
  val default: ActorNameGenerator = ActorNameGenerator("actor")
  val ask: ActorNameGenerator = ActorNameGenerator("anon-ask")
}