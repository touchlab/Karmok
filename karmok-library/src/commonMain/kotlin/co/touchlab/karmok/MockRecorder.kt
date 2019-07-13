package co.touchlab.karmok

import kotlin.reflect.KProperty

abstract class MockManager(
    internal var delegate:Any?,
    internal var recordCalls:Boolean
    ) {

    private data class CannedResult<RT>(val r:RT, var times:Int)

    inner class MockRecorder<T, RT> () {

        val emptyList = emptyList<Any?>().freeze()

        private var invokedCount = 0
        private var invokedParametersList = mutableListOf<List<Any?>>()
        private var stubbedError : Throwable? = null
        private var stubbedResult = mutableListOf<CannedResult<RT>>()

        val called : Boolean
            get() = invokedCount > 0

        val calledCount : Int
            get() = invokedCount

        fun throwOnCall(t:Throwable){
            stubbedError = t
        }

        fun returns(rt:RT, times: Int = 1):MockRecorder<T, RT>{
            stubbedResult.add(CannedResult(rt, times))
            return this
        }

        fun invokeCount(args:List<Any?>){
            invokedCount++

            if(recordCalls)
                invokedParametersList.add(ArrayList(args))

            if(stubbedError != null)
                throw stubbedError!!
        }

        fun invokeUnit(dblock:T.()->Unit, args:List<Any?>){
            invokeCount(args)
            if(delegate != null){
                (delegate as T).dblock()
            }
        }

        fun invoke(dblock:T.()->RT, args:List<Any?>):RT{
            invokeCount(args)
            if(stubbedResult.isNotEmpty())
                return stubbedResult!!

            if(delegate != null){
                return (delegate as T).dblock()
            }

            throw NullPointerException()
        }

        fun invoke(dblock:T.()->RT):RT = invoke(dblock, emptyList)
    }

    inner class MockProperty<T, RT>(private val dget:T.()->RT, private val dset:T.(RT)->Unit) {
        val emptyList = emptyList<RT>().freeze()

        private var invokedCountGet = 0
        private var invokedCountSet = 0
        private var invokedParametersListSet = mutableListOf<RT>()
        private var stubbedError : Throwable? = null
        private var stubbedResult : RT? = null

        val called : Boolean
            get() = invokedCountGet > 0 || invokedCountSet > 0

        val calledCountGet : Int
            get() = invokedCountGet

        val calledCountSet : Int
            get() = invokedCountSet

        fun throwOnCall(t:Throwable){
            stubbedError = t
        }

        fun returnOnCall(rt:RT){
            stubbedResult = rt
        }

        operator fun getValue(thisRef: Any?, property: KProperty<*>): RT {
            try {
                if(stubbedResult != null)
                    return stubbedResult!!

                if(delegate != null){
                    return (delegate as T).dget()
                }

            } finally {
                invokedCountGet++
            }

            throw NullPointerException()
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: RT) {
            if (delegate != null) {
                (delegate as T).dset(value)
            } else {
                stubbedResult = value
            }

            if(recordCalls)
                invokedParametersListSet.add(value)

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