@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package io.github.kunal26das.startup

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.free
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import platform.posix.PTHREAD_MUTEX_RECURSIVE
import platform.posix.pthread_mutex_destroy
import platform.posix.pthread_mutex_init
import platform.posix.pthread_mutex_lock
import platform.posix.pthread_mutex_t
import platform.posix.pthread_mutex_trylock
import platform.posix.pthread_mutex_unlock
import platform.posix.pthread_mutexattr_destroy
import platform.posix.pthread_mutexattr_init
import platform.posix.pthread_mutexattr_settype
import platform.posix.pthread_mutexattr_t
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.Cleaner
import kotlin.native.ref.createCleaner

internal actual class StartupMutex actual constructor() {

    private val mutex: CPointer<pthread_mutex_t> = nativeHeap.alloc<pthread_mutex_t>().ptr

    init {
        memScoped {
            val attributes = alloc<pthread_mutexattr_t>()
            check(pthread_mutexattr_init(attributes.ptr) == 0)
            check(pthread_mutexattr_settype(attributes.ptr, PTHREAD_MUTEX_RECURSIVE) == 0)
            check(pthread_mutex_init(mutex, attributes.ptr) == 0)
            pthread_mutexattr_destroy(attributes.ptr)
        }
    }

    private val cleaner: Cleaner = createCleaner(mutex) { pointer ->
        pthread_mutex_destroy(pointer)
        nativeHeap.free(pointer)
    }

    actual fun tryLock(): Boolean = pthread_mutex_trylock(mutex) == 0

    actual fun lock() {
        check(pthread_mutex_lock(mutex) == 0)
    }

    actual fun unlock() {
        check(pthread_mutex_unlock(mutex) == 0)
    }
}
