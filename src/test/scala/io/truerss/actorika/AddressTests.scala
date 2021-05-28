package io.truerss.actorika

import munit.Location

class AddressTests extends munit.FunSuite {

  test("merge") {
    val first = Address("system")
    val second = Address("foo")
    val third = Address("bar")
    assertEquals(first.merge(second).name, "system/foo")
    assertEquals(first.merge(second).merge(third).name, "system/foo/bar")
    assertEquals(
      first.merge(second).merge(third).merge("baz").name,
      "system/foo/bar/baz")
  }

  test("requirements check") {
    checkOk(() => Address("asd"))
    checkOk(() => Address("123"))
    checkOk(() => Address("123/asd"))
    checkOk(() => Address("123/asd-qwe"))
    checkOk(() => Address("123"))
  }

  test("failed requirements") {
    checkNotOk(() => Address("#"))
    checkNotOk(() => Address("?123"))
    checkNotOk(() => Address("@!asd"))
  }

  private def checkOk(f: () => Address)(implicit loc: Location): Unit = {
    try {
      assert(f().name.nonEmpty)
    } catch {
      case _: Throwable =>
        assert(false, "Oops, check the code")
    }
  }

  private def checkNotOk(f: () => Address)(implicit loc: Location): Unit = {
    try {
      f()
      assert(false)
    } catch {
      case _: Throwable =>
        assert(true)
    }
  }

}
