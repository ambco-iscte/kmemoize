package pt.iscte.ambco.kmemoize.api

/**
 * Allows calls to the annotated function to be memoized.
 * A memoized function stores calls in an auxiliary data structure
 * to avoid repeated computation of the same call.
 * **The annotated function _must be pure_.**
 *
 * **Example:**
 * ```
 *  @Memoize
 *  fun foo(p: T1): T2 {
 *      return bar(p)
 *  }
 *  ```
 *  Compiles to (identifiers may vary):
 *  ```
 *  private val fooMemory = mutableMapOf<T1, T2>()
 *
 *  @Memoize
 *  fun foo(p: T1): T2 {
 *      if (p !in fooMemory)
 *          fooMemory[p] = bar(p)
 *      return fooMemory[p]!!
 *  }
 *  ```
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Memoize

/**
 * Allows calls to the annotated function to be memoized.
 * A memoized function stores calls in an auxiliary data structure
 * to avoid repeated computation of the same call.
 * **The annotated function _does not need to be pure_.**
 *
 * For impure functions, subsequent calls will reuse to the memoized value corresponding to the result of the
 * first function call with the corresponding input.
 *
 * **Example:**
 * ```
 *  @UnsafeMemoize
 *  fun foo(p: T1): T2 {
 *      return random(p) // Impure, random operation (e.g., Math.random)
 *  }
 *  ```
 *  Compiles to (identifiers may vary):
 *  ```
 *  private val fooMemory = mutableMapOf<T1, T2>()
 *
 *  @UnsafeMemoize
 *  fun foo(p: T1): T2 {
 *      if (p !in fooMemory)
 *          fooMemory[p] = random(p)
 *      return fooMemory[p]!!
 *  }
 *  ```
 *
 *  @see Memoize
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
@RequiresOptIn("Forcibly memoizing impure functions may lead to unintended behaviour.")
annotation class UnsafeMemoize