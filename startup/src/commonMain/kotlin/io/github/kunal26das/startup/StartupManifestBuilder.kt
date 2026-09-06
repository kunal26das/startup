@file:OptIn(ExperimentalObjCRefinement::class)

package io.github.kunal26das.startup

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * Collects the entries of a [StartupManifest]. Obtained from `StartupManifest { }`.
 *
 * Registration order is preserved and is what makes the plan reproducible: the same
 * declarations always yield the same initialization order. Registering the same component
 * twice keeps the position of the first registration and the entry of the last, so a
 * later declaration overrides an earlier one.
 *
 * Every registration function comes in two shapes. The `reified` one names the component
 * at compile time and is what shared Kotlin code uses. The one taking an explicit
 * [AnyInitializerKey] names it at run time, which is what a host application handing
 * Kotlin a list of initializers needs, and is the only shape Swift and Objective-C can
 * call: a `reified` type argument cannot cross that boundary, so the `reified` functions
 * are [HiddenFromObjC] rather than exported with their type parameter erased.
 */
class StartupManifestBuilder internal constructor() {

    private val nodes = LinkedHashMap<AnyInitializerKey, Node>()

    /**
     * Registers [T] as an eagerly initialized component, the equivalent of a
     * `<meta-data android:value="androidx.startup" />` entry.
     *
     * [factory] must construct [T] with no arguments: AndroidX ignores it and reflects on
     * the class instead, so a factory that passes constructor arguments works everywhere
     * except Android, where it throws `StartupException(NoSuchMethodException)`.
     *
     * It must also build [T] itself and not a subclass. Kotlin function types are covariant
     * in their return type, so `metaData<Base> { Derived() }` compiles; it is rejected when
     * the graph is planned, on every target.
     */
    @HiddenFromObjC
    inline fun <reified T : Initializer<*>> metaData(noinline factory: () -> T) {
        metaData(initializerKey<T>(), factory)
    }

    /**
     * Registers [component] as an eagerly initialized component, under a key the caller
     * computed rather than one the compiler reified.
     *
     * This is what registers an initializer that only exists at run time, such as one a
     * host application passes in: `metaData(initializerKey(instance)) { instance }` puts
     * it in the graph, so it is ordered, deduplicated and diagnosed like every other
     * component instead of having to be run outside the graph.
     *
     * The same no-argument constructor rule as the `reified` overload applies on Android,
     * where the factory is ignored and the class named by [component] is reflected on.
     *
     * [factory] must also build exactly the class [component] names. Registering a subclass
     * under a supertype key is rejected when the graph is planned, because AndroidX reflects
     * [component] and ignores the factory, so accepting it would let one manifest build a
     * different graph on Android than everywhere else.
     */
    fun metaData(component: AnyInitializerKey, factory: () -> Initializer<*>) {
        nodes[component] = Node.Merge(factory)
    }

    /**
     * Registers [T] as a component created only when something asks for it, the
     * equivalent of a class that an Android app deliberately leaves out of its
     * `InitializationProvider` block.
     *
     * The same no-argument constructor and exact-class rules as [metaData] apply.
     */
    @HiddenFromObjC
    inline fun <reified T : Initializer<*>> lazyInitializer(noinline factory: () -> T) {
        lazyInitializer(initializerKey<T>(), factory)
    }

    /**
     * Registers [component] as a component created only when something asks for it, under
     * a key the caller computed rather than one the compiler reified. The same no-argument
     * constructor and exact-class rules as [metaData] apply.
     */
    fun lazyInitializer(component: AnyInitializerKey, factory: () -> Initializer<*>) {
        nodes[component] = Node.Lazy(factory)
    }

    /**
     * Hides [T], the equivalent of `tools:node="remove"`. Use it to drop an entry an
     * [include]d manifest contributed.
     *
     * On Android this only suppresses what [Startup.install] would otherwise start, and
     * only when nothing else pulls the component in: AndroidX reads `dependencies()`
     * reflectively without consulting a [StartupManifest], so a tombstoned component that
     * an eager component still depends on is created there regardless, while off Android
     * the same manifest fails to plan. A component that a library contributed through its
     * own AndroidManifest is created by `InitializationProvider` before any application
     * code runs, so nothing here can reach it; suppressing that needs a real
     * `tools:node="remove"` entry written by hand in the application's AndroidManifest.
     */
    @HiddenFromObjC
    inline fun <reified T : Initializer<*>> remove() {
        remove(initializerKey<T>())
    }

    /**
     * Hides [component], under a key the caller computed rather than one the compiler
     * reified. The same Android caveat as the `reified` overload applies.
     */
    fun remove(component: AnyInitializerKey) {
        nodes[component] = Node.Remove
    }

    /**
     * Merges [manifest] in. Its entries override anything registered so far, and anything
     * registered afterward overrides them.
     */
    fun include(manifest: StartupManifest) {
        nodes.putAll(manifest.nodes)
    }

    internal fun build(): StartupManifest = StartupManifest(LinkedHashMap(nodes))
}
