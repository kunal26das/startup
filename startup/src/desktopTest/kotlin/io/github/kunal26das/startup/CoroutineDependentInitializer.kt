package io.github.kunal26das.startup

/** Declares the suspending component as a dependency, and records when it runs. */
class CoroutineDependentInitializer : Initializer<Any> {

    /** Records one creation and returns an instance only equal to itself. */
    override fun create(context: Context): Any {
        WaveLog.record(NAME)
        return Any()
    }

    /** The whole point of this component: it must not start until the slow one is done. */
    override fun dependencies(): List<AnyInitializerKey> =
        listOf(initializerKey<CoroutineSlowInitializer>())

    /** What this component records. */
    companion object {

        /** What this component records. */
        const val NAME = "dependent"
    }
}
