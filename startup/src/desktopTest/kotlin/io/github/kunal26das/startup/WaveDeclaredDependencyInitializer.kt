package io.github.kunal26das.startup

/** Reads a declared dependency from an earlier wave through the engine under test. */
class WaveDeclaredDependencyInitializer(private val appInitializer: AppInitializer) : Initializer<Any> {

    /** Reads the component after the planner has already created it. */
    override fun create(context: Context): Any =
        appInitializer.initializeComponent(initializerKey<WaveOkInitializer>())

    /** Places the component being read into an earlier wave. */
    override fun dependencies(): List<AnyInitializerKey> = listOf(initializerKey<WaveOkInitializer>())
}
