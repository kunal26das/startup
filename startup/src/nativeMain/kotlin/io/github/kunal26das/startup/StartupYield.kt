package io.github.kunal26das.startup

/**
 * Offers the rest of this thread's slice to another runnable thread.
 *
 * [StartupLock] on Kotlin/Native is a compare-and-set loop rather than a parking lock,
 * because there is no parking primitive every native target shares, and a loop that only
 * spins holds its core for as long as the holder runs.
 *
 * Be clear about what this buys, because it is less than it looks: measured on macosArm64
 * with three threads contending on a one-second hold, yielding cost 98% of a core each
 * against 100% for a bare spin. Darwin's `sched_yield` returns to the same runnable thread
 * almost at once when a core is free, and donates no priority. It is the right call to make
 * here and it is not a fix — the fix is a parking lock per native family, which this
 * library still owes.
 */
internal expect fun startupYield()
