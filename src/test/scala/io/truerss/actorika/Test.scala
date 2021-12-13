package io.truerss.actorika

import munit.Location

trait Test extends munit.FunSuite {

  def assert1(
                cond: => Boolean,
                clue: => Any = "assertion failed",
                count: Int = 3
              )(implicit loc: Location): Unit = {
    if (count == 0) {
      assert(cond, clue)
    } else {
      if (cond) {
        assert(cond, clue)
      } else {
        Thread.sleep(100)
        assert1(cond, clue, count - 1)
      }
    }
  }

}
