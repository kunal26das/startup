package io.github.kunal26das.startup

import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Declared ordering and the runner's thread requirement are independent constraints. */
class WaveDeclaredDependencyTest {

    /** Clears the fixture's shared creation log after each isolated engine run. */
    @AfterTest
    fun clear() {
        WaveLog.reset()
    }

    /** A completed dependency is readable by a task on the installing thread. */
    @Test
    fun readsADeclaredDependencyOnTheInstallingThread() {
        val appInitializer = AppInitializer(DefaultContext)
        appInitializer.engine.install(manifest(appInitializer)) { wave -> wave.forEach { it() } }
        assertSame(
            appInitializer.initializeComponent(initializerKey<WaveOkInitializer>()),
            appInitializer.initializeComponent(initializerKey<WaveDeclaredDependencyInitializer>()),
        )
    }

    /** A declared dependency cannot bypass the engine lock from a worker task. */
    @Test
    fun refusesAWorkerReadOfAnAlreadyCompletedDependency() {
        val appInitializer = AppInitializer(DefaultContext)
        val failure = assertFailsWith<StartupException> {
            appInitializer.engine.install(manifest(appInitializer)) { wave ->
                val workerFailure = AtomicReference<Throwable?>(null)
                val workers = wave.map { task ->
                    thread(isDaemon = true) {
                        try {
                            task()
                        } catch (throwable: Throwable) {
                            workerFailure.compareAndSet(null, throwable)
                        }
                    }
                }
                workers.forEach { worker ->
                    worker.join(TIMEOUT_MILLIS)
                    assertTrue(!worker.isAlive, "worker did not finish")
                }
                workerFailure.get()?.let { throw it }
            }
        }
        assertContains(failure.cause?.message.orEmpty(), "Run this initializer on the installing thread")
        assertContains(failure.cause?.message.orEmpty(), "even for completed components")
    }

    private fun manifest(appInitializer: AppInitializer) = StartupManifest {
        metaData<WaveDeclaredDependencyInitializer> { WaveDeclaredDependencyInitializer(appInitializer) }
        lazyInitializer<WaveOkInitializer> { WaveOkInitializer() }
    }

    private companion object {

        private const val TIMEOUT_MILLIS = 5_000L
    }
}
