package io.github.kunal26das.startup

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The engine holds its lock across a whole install, so a wave task on another thread that
 * calls back into it can never be served. It used to wait for that anyway — a parking wait
 * on the JVM and a bare compare-and-set loop on Kotlin/Native, where it cost a core for as
 * long as the install ran and never ended. It now fails at once, with the sentence the
 * documentation always carried.
 *
 * The distinction that matters is *which* thread. A thread inside a task is waiting for
 * something that cannot arrive; a thread that merely asked for a component while an install
 * happened to be running is waiting for something that will, and it still waits.
 */
class StartupBarrierTest {

    /** Starts every test from an empty log and no recorded re-entry. */
    @BeforeTest
    fun reset() {
        WaveLog.reset()
        WaveReentry.reset()
    }

    /** Leaves no runtime behind for the next test to pick up. */
    @AfterTest
    fun clear() {
        WaveReentry.reset()
    }

    /** A task that resolves a component from another thread is refused rather than left waiting. */
    @Test
    fun refusesAReentrantCallFromAWaveTaskOnAnotherThread() {
        val appInitializer = AppInitializer(DefaultContext)
        WaveReentry.appInitializer = appInitializer
        appInitializer.engine.install(manifest(), threadPerTaskRunner())
        assertContains(WaveLog.recorded, WaveOkInitializer.NAME)
        assertContains(WaveLog.recorded, WaveReentrantInitializer.REFUSED)
        val failure = assertIs<StartupException>(WaveReentry.failure)
        assertContains(failure.message.orEmpty(), "running a WaveRunner task")
    }

    /**
     * The same call from the installing thread still works, because the lock is reentrant.
     *
     * This is the half of the 2.x rule that was always false, and it is the only shape a
     * runner can have on Kotlin/JS and Kotlin/Wasm.
     */
    @Test
    fun servesAReentrantCallFromAWaveTaskOnTheInstallingThread() {
        val appInitializer = AppInitializer(DefaultContext)
        WaveReentry.appInitializer = appInitializer
        appInitializer.engine.install(manifest()) { wave -> wave.forEach { it() } }
        assertNull(WaveReentry.failure)
        assertContains(WaveLog.recorded, WaveReentrantInitializer.RESOLVED)
        assertContains(WaveLog.recorded, WaveLazyInitializer.NAME)
    }

    /**
     * A thread that is not running a task waits for the install and is then served.
     *
     * Refusing this one too would turn a correct, if slow, call into a crash, which is what
     * a lock barred for the whole install would have done.
     */
    @Test
    fun stillServesAThreadThatIsNotRunningAWaveTask() {
        val appInitializer = AppInitializer(DefaultContext)
        WaveReentry.appInitializer = appInitializer
        val outcome = AtomicReference<Any?>(null)
        val failure = AtomicReference<Throwable?>(null)
        val started = CountDownLatch(1)
        var bystander: Thread? = null
        appInitializer.engine.install(onlyOk()) { wave ->
            bystander = thread(isDaemon = true) {
                started.countDown()
                try {
                    outcome.set(appInitializer.initializeComponent(initializerKey<WaveLazyInitializer>()))
                } catch (throwable: Throwable) {
                    failure.set(throwable)
                }
            }
            started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            Thread.sleep(CONTENTION_MILLIS)
            wave.forEach { it() }
        }
        bystander?.join(TIMEOUT_SECONDS * MILLIS_PER_SECOND)
        assertTrue(bystander?.isAlive != true, "the bystander never finished")
        assertNull(failure.get())
        assertNotNull(outcome.get())
        assertEquals(listOf(WaveOkInitializer.NAME, WaveLazyInitializer.NAME), WaveLog.recorded)
    }

    private fun manifest() = StartupManifest {
        metaData<WaveOkInitializer> { WaveOkInitializer() }
        metaData<WaveReentrantInitializer> { WaveReentrantInitializer() }
        lazyInitializer<WaveLazyInitializer> { WaveLazyInitializer() }
    }

    private fun onlyOk() = StartupManifest {
        metaData<WaveOkInitializer> { WaveOkInitializer() }
        lazyInitializer<WaveLazyInitializer> { WaveLazyInitializer() }
    }

    /** Runs every task on a thread of its own and waits for all of them, as a real runner does. */
    private fun threadPerTaskRunner() = WaveRunner { wave ->
        wave.map { task -> thread(isDaemon = true) { task() } }.forEach { it.join() }
    }

    private companion object {

        private const val TIMEOUT_SECONDS = 5L
        private const val CONTENTION_MILLIS = 200L
        private const val MILLIS_PER_SECOND = 1000L
    }
}
