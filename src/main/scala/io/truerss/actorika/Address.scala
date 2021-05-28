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
  require(name.matches("[a-zA-Z0-9/]*"))

  def merge(other: Address): Address = {
    Address(s"$name/${other.name}")
  }

  def merge(tail: String): Address = {
    Address(s"$name/$tail")
  }
}
