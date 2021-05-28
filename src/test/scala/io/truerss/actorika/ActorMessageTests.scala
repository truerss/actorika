package io.truerss.actorika

class ActorMessageTests extends munit.FunSuite {

  test("check isKill") {
    val msg = ActorMessage("1", ActorRef(Address("asd")), ActorRef(Address("cde")))
    val kill = msg.copy(message = Kill)

    assert(!msg.isKill)
    assert(kill.isKill)
  }

}
