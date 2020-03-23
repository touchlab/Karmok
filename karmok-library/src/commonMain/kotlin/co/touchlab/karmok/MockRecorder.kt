package co.touchlab.karmok

import kotlin.reflect.KProperty

sealed class Interaction
data class MethodCall(val source: MockManager.MockFieldRecorder<*, *>, val data: List<Any?>) : Interaction()
data class ValGet(val source: MockManager.MockPropertyRecorder<*, *>) : Interaction()
data class ValSet<RT>(val source: MockManager.MockPropertyRecorder<*, *>, val data: RT) : Interaction()

sealed class Matcher {
    abstract fun match(arg: Any?): Boolean
}

data class Exact<T>(val v: T) : Matcher() {
    override fun match(arg: Any?) = v == arg
}

object AnyMatch : Matcher() {
    override fun match(arg: Any?) = true
}

data class FuncMatch(val block: (Any?) -> Boolean) : Matcher() {
    override fun match(arg: Any?): Boolean = block(arg)
}

fun any() = AnyMatch
fun <T> exact(v: T) = Exact(v)
fun func(block: (Any?) -> Boolean) = FuncMatch(block)

abstract class MockManager(
    internal var delegate: Any?
) {

    companion object {
        val emptyList = emptyList<Any?>().freeze()
    }

    private data class CannedResult<RT>(val r: RT, var times: Int, val matchers: List<Matcher>)

    private var verifyPosition = 0

    private val globalInvokedParametersList = mutableListOf<Interaction>()

    internal fun nextInteraction(): Interaction {
        if(done)
            throw IllegalStateException("Interactions done")

        return globalInvokedParametersList.get(verifyPosition++)
    }

    fun resetVerify() {
        verifyPosition = 0
    }

    val done: Boolean
        get() {
            debugPrint()
            return verifyPosition == globalInvokedParametersList.size
        }

    fun debugPrint() {
        println("verifyPosition: $verifyPosition")
        globalInvokedParametersList.forEach {
            println("interaction: $it")
        }
    }

    open inner class MockData<T, RT>() {
        internal var stubbedError: Throwable? = null
    }

    interface MockField<RT> {
        fun calledWith(vararg args: Any?): Boolean
        val calledCount: Int
        fun throwOnCall(t: Throwable)
        fun returns(rt: RT, matchers: List<Matcher> = emptyList(), times: Int = Int.MAX_VALUE)
        fun invokeCount(args: List<Any?>)
    }

    inner class MockFieldRecorder<T, RT>() : MockData<T, RT>(), MockField<RT> {
        private var stubbedResult = mutableListOf<CannedResult<RT>>()

        override fun calledWith(vararg args: Any?): Boolean = nextInteraction() == MethodCall(this, args.asList())

        override val calledCount: Int
            get() = globalInvokedParametersList.filterIsInstance<MethodCall>()
                .count { it.source === this }

        override fun throwOnCall(t: Throwable) {
            stubbedError = t
        }

        override fun returns(rt: RT, matchers: List<Matcher>, times: Int) {
            stubbedResult.add(CannedResult(rt, times, matchers))
        }

        override fun invokeCount(args: List<Any?>) {
            val element = MethodCall(this, ArrayList(args))
            globalInvokedParametersList.add(element)

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
            val stubbedMatch = stubbedResult.firstOrNull {
                it.matchers.isEmpty() ||
                        (it.matchers.size == args.size &&
                                it.matchers.mapIndexed { index, matcher ->
                                    matcher.match(args[index])
                                }.all { it }
                                )
            }
            val result = when {
                stubbedMatch != null -> {
                    stubbedMatch.times--
                    if (stubbedMatch.times == 0)
                        stubbedResult.remove(stubbedMatch)
                    stubbedMatch.r
                }
                delegate != null -> (delegate as T).dblock()
                else -> throw NullPointerException()
            }

            invokeCount(args)
            return result
        }

        fun invoke(dblock: T.() -> RT): RT = invoke(dblock, emptyList)
    }

    interface MockProperty<RT> {
        val getCalled: Boolean
        fun setCalled(rt: RT): Boolean
        val calledCountGet: Int
        val calledCountSet: Int
        fun throwOnCall(t: Throwable)
        fun returnOnCall(rt: RT)
    }

    inner class MockPropertyRecorder<T, RT>(private val dget: T.() -> RT, private val dset: T.(RT) -> Unit) :
        MockData<T, RT>(), MockProperty<RT> {

        private var stubbedResult: RT? = null

        override val getCalled: Boolean
            get() = nextInteraction() == ValGet(this)

        override fun setCalled(rt: RT): Boolean = nextInteraction() == ValSet(this, rt)

        override val calledCountGet: Int
            get() = globalInvokedParametersList.filterIsInstance<ValGet>()
                .count { it.source === this }

        override val calledCountSet: Int
            get() = globalInvokedParametersList.filterIsInstance<ValSet<*>>()
                .count { it.source === this }

        override fun throwOnCall(t: Throwable) {
            stubbedError = t
        }

        override fun returnOnCall(rt: RT) {
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
                val interaction = ValGet(this)
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

            val interaction = ValSet(this, value)
            globalInvokedParametersList.add(interaction)
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