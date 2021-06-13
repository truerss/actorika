package io.truerss.actorika

class ActorNameGeneratorTests extends munit.FunSuite {

  test("generator") {
    val custom = ActorNameGenerator("custom")
    assert(custom.next(1) == "custom-1")
  }

}
