package co.touchlab.karmok

import kotlin.reflect.KProperty

sealed class Interaction
data class MethodCall(val source: MockManager.MockRecorder<*, *>, val data: List<Any?>) : Interaction()
data class ValGet(val source: MockManager.MockProperty<*, *>) : Interaction()
data class ValSet<RT>(val source: MockManager.MockProperty<*, *>, val data: RT) : Interaction()

abstract class MockManager(
    internal var delegate: Any?,
    internal var recordCalls: Boolean
) {

    private data class CannedResult<RT>(val r: RT, var times: Int)

    private var verifyPosition = 0

    private val globalInvokedParametersList = mutableListOf<Interaction>()

    internal fun nextInteraction() = globalInvokedParametersList.get(verifyPosition++)

    fun resetVerify() {
        verifyPosition = 0
    }

    val done: Boolean
        get() {
            debugPrint()
            return verifyPosition == globalInvokedParametersList.size
        }

    fun debugPrint(){
        println("verifyPosition: $verifyPosition")
        globalInvokedParametersList.forEach {
            println("interaction: $it")
        }
    }

    inner class MockRecorder<T, RT>() {

        val emptyList = emptyList<Any?>().freeze()

        private var invokedCount = 0
        private val interactionList = mutableListOf<MethodCall>()
        private var stubbedError: Throwable? = null
        private var stubbedResult = mutableListOf<CannedResult<RT>>()

        fun calledWith(args: List<Any?>): Boolean = nextInteraction() == MethodCall(this, args)

        val called: Boolean
            get() = invokedCount > 0

        val calledCount: Int
            get() = invokedCount

        fun throwOnCall(t: Throwable) {
            stubbedError = t
        }

        fun returns(rt: RT, times: Int = 1): MockRecorder<T, RT> {
            stubbedResult.add(CannedResult(rt, times))
            return this
        }

        fun invokeCount(args: List<Any?>) {
            invokedCount++

            if (recordCalls) {
                val element = MethodCall(this, ArrayList(args))
                interactionList.add(element)
                globalInvokedParametersList.add(element)
            }

            if (stubbedError != null)
                throw stubbedError!!
        }

        fun invokeUnit(dblock: T.() -> Unit, args: List<Any?>) {
            invokeCount(args)
            if (delegate != null) {
                (delegate as T).dblock()
            }
        }

        fun invoke(dblock: T.() -> RT, args: List<Any?>): RT {
            val result = if (stubbedResult.isNotEmpty()) {
                stubbedResult.removeAt(0).r
            } else if (delegate != null) {
                (delegate as T).dblock()
            } else {
                throw NullPointerException()
            }

            invokeCount(args)
            return result
        }

        fun invoke(dblock: T.() -> RT): RT = invoke(dblock, emptyList)
    }

    inner class MockProperty<T, RT>(private val dget: T.() -> RT, private val dset: T.(RT) -> Unit) {
        val emptyList = emptyList<RT>().freeze()

        private var invokedCountGet = 0
        private var invokedCountSet = 0
        private val interactionList = mutableListOf<Interaction>()
        private var stubbedError: Throwable? = null
        private var stubbedResult: RT? = null

        val getCalled: Boolean
            get() = nextInteraction() == ValGet(this)

        fun setCalled(rt: RT): Boolean = nextInteraction() == ValSet(this, rt)

        val called: Boolean
            get() = invokedCountGet > 0 || invokedCountSet > 0

        val calledCountGet: Int
            get() = invokedCountGet

        val calledCountSet: Int
            get() = invokedCountSet

        fun throwOnCall(t: Throwable) {
            stubbedError = t
        }

        fun returnOnCall(rt: RT) {
            stubbedResult = rt
        }

        operator fun getValue(thisRef: Any?, property: KProperty<*>): RT {
            try {
                if (stubbedResult != null)
                    return stubbedResult!!

                if (delegate != null) {
                    return (delegate as T).dget()
                }

            } finally {
                invokedCountGet++
                val interaction = ValGet(this)
                interactionList.add(interaction)
                globalInvokedParametersList.add(interaction)
            }

            throw NullPointerException()
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: RT) {
            if (delegate != null) {
                (delegate as T).dset(value)
            } else {
                stubbedResult = value
            }

            if (recordCalls) {
                val interaction = ValSet(this, value)
                interactionList.add(interaction)
                globalInvokedParametersList.add(interaction)
            }

            invokedCountSet++
        }
    }
}

expect class AtomicInt(initialValue: Int) {
    fun get(): Int
    fun set(newValue: Int)
    fun incrementAndGet(): Int
    fun decrementAndGet(): Int

    fun addAndGet(delta: Int): Int
    fun compareAndSet(expected: Int, new: Int): Boolean
}

internal var AtomicInt.value
    get() = get()
    set(value) {
        set(value)
    }

expect class AtomicReference<V>(initialValue: V) {
    fun get(): V
    fun set(value_: V)

    /**
     * Compare current value with expected and set to new if they're the same. Note, 'compare' is checking
     * the actual object id, not 'equals'.
     */
    fun compareAndSet(expected: V, new: V): Boolean
}

internal var <T> AtomicReference<T>.value: T
    get() = get()
    set(value) {
        set(value)
    }

internal expect fun <T> T.freeze(): T