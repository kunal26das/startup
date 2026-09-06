package io.github.kunal26das.startup

/**
 * Resolves [AlphaInitializer] from inside [create] while both are registered eagerly and
 * neither declares the other, so the execution loop still has [AlphaInitializer] ahead of
 * it when the nested call has already created it.
 */
class ForwardCallerInitializer : BaseInitializer<String>() {

    /** Creates [AlphaInitializer] out of turn, then records its own creation. */
    override fun create(context: Context): String {
        Startup.getInstance(context).initializeComponent(initializerKey<AlphaInitializer>())
        TestLog.record("forwardCaller")
        return "forwardCaller"
    }
}
