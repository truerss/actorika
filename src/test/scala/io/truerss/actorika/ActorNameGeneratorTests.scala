package io.truerss.actorika

class ActorNameGeneratorTests extends munit.FunSuite {

  test("generator") {
    val custom = ActorNameGenerator.create("custom")
    (0 to 3).foreach { x =>
      assert(custom.next() == s"custom-$x")
    }
  }
}
