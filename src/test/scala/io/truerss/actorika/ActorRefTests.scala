package io.truerss.actorika

import java.util.concurrent.{ConcurrentLinkedQueue => CLQ}

class ActorRefTests extends munit.FunSuite {

  import scala.jdk.CollectionConverters._

  private val to = ActorRef(Address("test/foo"))
  private val me = ActorRef(Address("test/bar"))

  test("should add to mailbox [#send]") {
    val xs = (0 to 10)
    xs.foreach { x =>
      me.send(to, x)
    }

    assertEquals(me.associatedMailbox.isEmpty, true)
    assertEquals(to.associatedMailbox.size(), xs.size)
    val msgs = to.associatedMailbox.asScala
    msgs.foreach { msg =>
      assert(!msg.isKill)
      assertEquals(msg.to, to)
      assertEquals(msg.from, me)
    }
    assertEquals(msgs.map(_.message.asInstanceOf[Int]), xs)
  }

  test("check path") {
    assertEquals(to.path, "test/foo")
    assertEquals(me.path, "test/bar")
    assert(!to.isSystemRef)
    assert(!me.isSystemRef)
  }

  test("create as systemRef") {
    val ref = ActorRef(Address("system"), new CLQ[ActorTellMessage]())
    assert(!ref.isSystemRef)
    val sRef= ActorRef(Address("system"), true, new CLQ[ActorTellMessage]())
    assert(sRef.isSystemRef)
  }

}
