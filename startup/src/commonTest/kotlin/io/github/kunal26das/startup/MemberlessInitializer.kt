package io.github.kunal26das.startup

/**
 * The memberless platform-specific initializer shape: an `expect class` extending
 * [BaseInitializer] with no body at all. `create` is an inherited abstract member, so the
 * `actual` overrides it without the `actual` modifier and the `expect` never names it.
 *
 * **This shape is not portable, and this file may not be moved out of a test source set.**
 * It compiles, links and runs on all eleven targets, and it is rejected by
 * `compileCommonMainKotlinMetadata` with *Class 'MemberlessInitializer' is not abstract
 * and does not implement abstract member*, which is a general Kotlin rule about an
 * `expect class` inheriting an unimplemented abstract member rather than anything this
 * library can reshape. Test source sets get no metadata compilation, which is the only
 * reason this file is green; [PlatformInitializer] is the shape to write in a source set
 * that has one, and `sample`'s `RuntimeInfoInitializer` is where that is enforced.
 */
expect class MemberlessInitializer() : BaseInitializer<String>
