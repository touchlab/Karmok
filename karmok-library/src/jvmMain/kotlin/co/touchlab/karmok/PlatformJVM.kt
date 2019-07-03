package co.touchlab.karmok

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

actual typealias AtomicInt = AtomicInteger
actual typealias AtomicReference<V> = AtomicReference<V>
actual fun <T> T.freeze(): T = this