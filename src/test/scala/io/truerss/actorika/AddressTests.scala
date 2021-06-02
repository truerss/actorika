package io.truerss.actorika

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
    assert(Address.isValid("asd"))
    assert(Address.isValid("123"))
    assert(Address.isValid("123/asd"))
    assert(Address.isValid("123/asd-qwe"))
  }

  test("failed requirements") {
    assert(!Address.isValid("#"))
    assert(!Address.isValid("?123"))
    assert(!Address.isValid("@!asd"))
  }

}
