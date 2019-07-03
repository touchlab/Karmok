package co.touchlab.scratch

import co.touchlab.stately.collections.frozenLinkedList
import co.touchlab.stately.concurrency.AtomicBoolean
import co.touchlab.stately.concurrency.AtomicInt
import co.touchlab.stately.concurrency.AtomicReference
import co.touchlab.stately.concurrency.value

class Mocker {
    private val saveRecorder = MockRecorder<Boolean>()

    fun save(data: Data, yurl: Yurl): Boolean = saveRecorder.invoke(listOf(data, yurl))
}

class MockRecorder<RT>{
    val invokedSave = AtomicBoolean(false)
    var invokedSaveCount = AtomicInt(0)
    var invokedSaveParametersList = frozenLinkedList<List<Any?>>()
    var stubbedSaveError = AtomicReference<Throwable?>(null)
    var stubbedSaveResult = AtomicReference<RT?>(null)

    fun invokeUnit(args:List<Any?>){
        invokedSave.value = true
        invokedSaveCount.incrementAndGet()
        invokedSaveParametersList.add(args)
        if(stubbedSaveError.value != null)
            throw stubbedSaveError.value!!
    }

    fun invoke(args:List<Any?>):RT{
        invokeUnit(args)
        if(stubbedSaveResult.value != null)
            return stubbedSaveResult.value!!

        throw NullPointerException()
    }
}

data class Data(val s:String)

data class Yurl(val s:String)