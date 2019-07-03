package co.touchlab.karmok

import kotlin.reflect.KProperty

abstract class MockManager {
    internal var delegate:Any? = null

    inner class MockRecorder<T, RT> () {

        val emptyList = emptyList<Any?>().freeze()

        private var invokedCount = AtomicInt(0)
        private var invokedParametersList = AtomicReference<List<Any?>>(emptyList)
        private var stubbedError = AtomicReference<Throwable?>(null)
        private var stubbedResult = AtomicReference<RT?>(null)

        val called : Boolean
            get() = invokedCount.value > 0

        val calledCount : Int
            get() = invokedCount.value

        fun throwOnCall(t:Throwable){
            stubbedError.value = t.freeze()
        }

        fun returnOnCall(rt:RT){
            stubbedResult.value = rt.freeze()
        }

        fun invokeCount(args:List<Any?>){
            invokedCount.incrementAndGet()

            //Threads are tough
            while (true){
                val lastList = invokedParametersList.value
                val newList = ArrayList(lastList)
                newList.add(args)
                if(invokedParametersList.compareAndSet(lastList, newList.freeze()))
                    break
            }

            if(stubbedError.value != null)
                throw stubbedError.value!!
        }

        fun invokeUnit(dblock:T.()->Unit, args:List<Any?>){
            invokeCount(args)
            if(delegate != null){
                (delegate as T).dblock()
            }
        }

        fun invoke(dblock:T.()->RT, args:List<Any?>):RT{
            invokeCount(args)
            if(stubbedResult.value != null)
                return stubbedResult.value!!

            if(delegate != null){
                return (delegate as T).dblock()
            }

            throw NullPointerException()
        }

        fun invoke(dblock:T.()->RT):RT = invoke(dblock, emptyList)
    }

    inner class MockProperty<T, RT>(private val dget:T.()->RT, private val dset:T.(RT)->Unit) {
        val emptyList = emptyList<RT>().freeze()

        private var invokedCountGet = AtomicInt(0)
        private var invokedCountSet = AtomicInt(0)
        private var invokedParametersListSet = AtomicReference(emptyList)
        private var stubbedError = AtomicReference<Throwable?>(null)
        private val stubbedResult = AtomicReference<RT?>(null)

        val called : Boolean
            get() = invokedCountGet.value > 0 || invokedCountSet.value > 0

        val calledCountGet : Int
            get() = invokedCountGet.value

        val calledCountSet : Int
            get() = invokedCountSet.value

        fun throwOnCall(t:Throwable){
            stubbedError.value = t.freeze()
        }

        fun returnOnCall(rt:RT){
            stubbedResult.value = rt.freeze()
        }

        operator fun getValue(thisRef: Any?, property: KProperty<*>): RT {
            try {
                if(stubbedResult.value != null)
                    return stubbedResult.value!!

                if(delegate != null){
                    return (delegate as T).dget()
                }

            } finally {
                invokedCountGet.incrementAndGet()
            }

            throw NullPointerException()
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: RT) {
            if (delegate != null) {
                (delegate as T).dset(value)
            } else {
                stubbedResult.value = value.freeze()
            }

            invokedCountSet.incrementAndGet()
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