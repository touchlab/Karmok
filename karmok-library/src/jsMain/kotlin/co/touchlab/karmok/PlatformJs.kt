/*
 * Copyright (c) 2020 Touchlab
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

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
