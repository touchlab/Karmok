package co.touchlab.karmok

actual class AtomicReference<V> actual constructor(initialValue: V) {
    private var internalValue: V = initialValue

    /**
     * Compare current value with expected and set to new if they're the same. Note, 'compare' is checking
     * the actual object id, not 'equals'.
     */
    actual fun compareAndSet(expected: V, new: V): Boolean {
        return if (expected === internalValue) {
            internalValue = new
            true
        } else {
            false
        }
    }

    actual fun get(): V = internalValue

    actual fun set(value_: V) {
        internalValue = value_
    }

}

internal actual fun <T> T.freeze(): T = this

actual class AtomicInt actual constructor(initialValue: Int) {
    private var internalValue: Int = initialValue

    actual fun addAndGet(delta: Int): Int {
        internalValue += delta
        return internalValue
    }

    actual fun compareAndSet(expected: Int, new: Int): Boolean {
        return if (expected == internalValue) {
            internalValue = new
            true
        } else {
            false
        }
    }

    actual fun get(): Int = internalValue

    actual fun set(newValue: Int) {
        internalValue = newValue
    }

    actual fun incrementAndGet(): Int = addAndGet(1)

    actual fun decrementAndGet(): Int = addAndGet(-1)
}
