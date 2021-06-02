package io.truerss.actorika

class ActorHierarchyTests extends munit.FunSuite {

  import scala.jdk.CollectionConverters._

  private case class Allocate(name: String)
  private case object StopMe

  private class TestActor extends Actor {
    def receive = {
      case Allocate(name) =>
        spawn(new FirstChild, name)
      case StopMe =>
        stop()
    }
  }

  private class FirstChild extends Actor {
    def receive = {
      case Allocate(name) =>
        spawn(new SecondChild, name)
      case StopMe =>
        stop()
    }
  }

  private class SecondChild extends Actor {
    def receive = {
      case StopMe =>
        stop()
    }
  }

  test("allocate new children") {
    val system = ActorSystem("system")
    val ref = system.spawn(new TestActor, "test")
    val xs = (0 to 3)
    xs.foreach { x =>
      // test/ch-x
      system.send(ref, Allocate(s"ch-$x"))
    }
    while (ref.hasMessages) {
      system.world.get(ref.path).tick()
    }
    Thread.sleep(100)
    assertEquals(system.world.size(), xs.size + 1)
    // add to every children for one actor
    val ch = system.world.asScala.filter(x => x._1.contains("ch-"))
    ch.values.map(_.ref).toVector.sortBy(_.path).zipWithIndex.foreach { x =>
      system.send(x._1, Allocate(s"ch-ch-${x._2}"))
    }
    ch.foreach { x =>
      while (x._2.ref.hasMessages) {
        x._2.tick()
      }
    }
    Thread.sleep(100)
    assertEquals(system.world.size(), xs.size + ch.size + 1)
    // stop then
    val last = system.world.asScala.filter(x => x._2.ref.path.contains("ch-ch-3")).head._2.ref
    system.stop(last)
    Thread.sleep(100)
    assertEquals(system.world.size(), xs.size + ch.size)
    // stop first child then
    val first = ch.values.filter(x => x.ref.path.contains("ch-0")).head
    system.stop(first.ref)
    Thread.sleep(100)
    val tst = system.world.keys().asScala.filter(x => x.contains("ch-0") || x.contains("ch-ch-0"))
    assertEquals(tst.size, 0)
    val ch3 = system.world.asScala.filter(x => x._2.ref.path.contains("ch-3")).head._2
    assertEquals(ch3.actor._children.size(), 0)
    Thread.sleep(100)
    assertNotEquals(last.path, first.ref.path)
    assertEquals(system.world.size(), xs.size + ch.size - 2) // 2 => first child + second actor
    // stop full hierarchy
    system.stop(ref)
    Thread.sleep(100)
    assert(system.world.isEmpty)
  }

  test("stop actors programmatically") {
    val system = ActorSystem("system")
    val ref = system.spawn(new TestActor, "test")
    val xs = (0 to 3)
    xs.foreach { x =>
      // test/ch-x
      system.send(ref, Allocate(s"ch-$x"))
    }
    while (ref.hasMessages) {
      system.world.get(ref.path).tick()
    }
    Thread.sleep(100)
    assertEquals(system.world.size(), xs.size + 1)
    // add to every children for one actor
    val ch = system.world.asScala.filter(x => x._1.contains("ch-"))
    ch.values.map(_.ref).toVector.sortBy(_.path).zipWithIndex.foreach { x =>
      system.send(x._1, Allocate(s"ch-ch-${x._2}"))
    }
    ch.foreach { x =>
      while (x._2.ref.hasMessages) {
        x._2.tick()
      }
    }
    Thread.sleep(100)
    assertEquals(system.world.size(), xs.size + ch.size + 1)
    // stop then
    val lastA = system.world.asScala.filter(x => x._2.ref.path.contains("ch-ch-3")).head
    val last = lastA._2.ref
    system.send(last, StopMe)
    while (last.hasMessages) {
      lastA._2.tick()
    }
    Thread.sleep(100)
    assertEquals(system.world.size(), xs.size + ch.size)
    // stop first child then
    val first = ch.values.filter(x => x.ref.path.contains("ch-0")).head
    system.send(first.ref, StopMe)
    while (first.ref.hasMessages) {
      first.tick()
    }
    Thread.sleep(100)
    val tst = system.world.keys().asScala.filter(x => x.contains("ch-0") || x.contains("ch-ch-0"))
    assertEquals(tst.size, 0)
    val ch3 = system.world.asScala.filter(x => x._2.ref.path.contains("ch-3")).head._2
    assertEquals(ch3.actor._children.size(), 0)
    Thread.sleep(100)
    assertNotEquals(last.path, first.ref.path)
    assertEquals(system.world.size(), xs.size + ch.size - 2) // 2 => first child + second actor
    // stop full hierarchy
    system.send(ref, StopMe)
    while (ref.hasMessages) {
      system.world.get(ref.path).tick()
    }
    Thread.sleep(100)
    assert(system.world.isEmpty)
  }

}
