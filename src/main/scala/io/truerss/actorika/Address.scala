package io.truerss.actorika

/**
 * Actor Address in system
 * system-name/actor-name
 * or
 * actor-name/child-name/.../child-name
 *
 * @param name
 */
case class Address(name: String) {
  def merge(other: Address): Address = {
    Address(s"$name/${other.name}")
  }

  def merge(tail: String): Address = {
    Address(s"$name/$tail")
  }
}

object Address {
  val rx = "[a-zA-Z0-9/-]*"
  def isValid(input: String): Boolean = {
    input.matches(rx)
  }
}
