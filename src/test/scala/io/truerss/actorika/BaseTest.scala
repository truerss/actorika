package io.truerss.actorika

import munit.Location

trait BaseTest extends munit.FunSuite {

  def checkEquals[A, B](a: A, b: B)(implicit loc: Location, ev: B <:< A): Unit = {
    assertEquals[A, B](a, b)
  }

  def check(cond: => Boolean)(implicit loc: Location): Unit = {
    val r = cond
    if (!r) {
      println(s"------> ${loc.line}: $r")
    }
    assert(cond)
  }

}
