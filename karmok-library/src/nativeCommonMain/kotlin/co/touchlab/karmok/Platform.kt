package co.touchlab.karmok

import kotlin.native.concurrent.AtomicReference
import kotlin.native.concurrent.AtomicInt
import kotlin.native.concurrent.freeze

actual class AtomicReference<V> actual constructor(initialValue: V) {
    private val atom = AtomicReference(initialValue)
    actual fun get(): V = atom.value

    actual fun set(value_: V) {
        atom.value = value_
    }

    /**
     * Compare current value with expected and set to new if they're the same. Note, 'compare' is checking
     * the actual object id, not 'equals'.
     */
    actual fun compareAndSet(expected: V, new: V): Boolean = atom.compareAndSet(expected, new)
}

actual class AtomicInt actual constructor(initialValue:Int){
    private val atom = AtomicInt(initialValue)

    actual fun get(): Int = atom.value

    actual fun set(newValue: Int) {
        atom.value = newValue
    }

    actual fun incrementAndGet(): Int = atom.addAndGet(1)

    actual fun decrementAndGet(): Int = atom.addAndGet(-1)

    actual fun addAndGet(delta: Int): Int = atom.addAndGet(delta)

    actual fun compareAndSet(expected: Int, new: Int): Boolean = atom.compareAndSet(expected, new)
}

actual fun <T> T.freeze(): T = this.freeze()