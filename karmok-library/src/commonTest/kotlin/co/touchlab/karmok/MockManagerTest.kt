package co.touchlab.karmok

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class MockManagerTest {
    @Test
    fun basicMocks() {
        val m = MockArst()
        m.mock.a_fun.returns("arst")
        m.mock.d_prop.returnOnCall("qwert")

        assertEquals(m.a(), "arst")
        assertEquals(m.d, "qwert")
        assertFails { m.c(null) }
        assertTrue(m.mock.a_fun.calledWith(emptyList()))
        assertTrue(m.mock.d_prop.getCalled)
        assertTrue(m.mock.done)
    }
}

class MockArst : Arst {
    internal val mock = InnerMock()
    override val d: String by mock.d_prop
    override var e: String by mock.e_prop
    override fun a(): String {
        return mock.a_fun.invoke({ a() }, listOf())
    }

    override fun b(s: String) {
        mock.b_fun.invokeUnit({ b(s) }, listOf(s))
    }

    override fun c(s: String?): String? {
        return mock.c_fun.invoke({ c(s) }, listOf(s))
    }

    inner class InnerMock(delegate: Any? = null, recordCalls: Boolean = true) : MockManager(delegate, recordCalls) {
        internal val a_fun = MockRecorder<MockArst, String>()
        internal val b_fun = MockRecorder<MockArst, Unit>()
        internal val c_fun = MockRecorder<MockArst, String?>()
        internal val d_prop = MockProperty<MockArst, String>({ d }) {}
        internal val e_prop = MockProperty<MockArst, String>({ e }) { e = it }
    }
}

interface Arst {
    fun a(): String
    fun b(s: String)
    fun c(s: String?): String?
    val d: String
    var e: String
}