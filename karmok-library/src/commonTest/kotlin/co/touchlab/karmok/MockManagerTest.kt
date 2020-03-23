package co.touchlab.karmok

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/*class MockManagerTest {
    @Test
    fun basicMocks() {
        val m = MockArst()
        m.mock.a.returns("arst")
        m.mock.d.returnOnCall("qwert")
        m.mock.c.returns("bbb", listOf(exact("Hey Hey")))

        assertFails { m.c(null) }

        m.mock.c.returns("ccc", listOf(any()))
        m.mock.f.returns("www", listOf(func {
            (it as String) == "Hiya"
        }))

        assertEquals(m.c(null), "ccc")

        assertEquals(m.a(), "arst")
        assertEquals(m.d, "qwert")
        assertEquals(m.c("arst"), "ccc")
        assertEquals(m.c("Hey Hey"), "bbb")
        assertEquals(m.f("Hiya"), "www")

        assertTrue(m.mock.c.calledWith(null))
        assertTrue(m.mock.a.calledWith())
        assertTrue(m.mock.d.getCalled)
        assertTrue(m.mock.c.calledWith("arst"))
        assertTrue(m.mock.c.calledWith("Hey Hey"))
        assertTrue(m.mock.f.calledWith("Hiya"))
        assertTrue(m.mock.done)
    }
}

class MockArst : Arst {
    internal val mock = InnerMock()
    override fun f(s: String): String {
        return mock.f.invoke({ f(s) }, listOf(s))
    }

    override val d: String by mock.d
    override var e: String by mock.e
    override fun a(): String {
        return mock.a.invoke({ a() }, listOf())
    }

    override fun b(s: String) {
        mock.b.invokeUnit({ b(s) }, listOf(s))
    }

    override fun c(s: String?): String? {
        return mock.c.invoke({ c(s) }, listOf(s))
    }


    inner class InnerMock(delegate: Any? = null, recordCalls: Boolean = true) : MockManager(delegate, recordCalls) {
        internal val f = MockRecorder<MockArst, String>()
        internal val a = MockRecorder<MockArst, String>()
        internal val b = MockRecorder<MockArst, Unit>()
        internal val c = MockRecorder<MockArst, String?>()
        internal val d = MockProperty<MockArst, String>({ d }) {}
        internal val e = MockProperty<MockArst, String>({ e }) { e = it }
    }

}*/

interface Arst {
    fun a(): String
    fun b(s: String)
    fun c(s: String?): String?
    val d: String
    var e: String
    fun f(s:String): String
}