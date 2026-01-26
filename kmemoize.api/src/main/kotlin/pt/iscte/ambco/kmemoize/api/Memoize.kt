package pt.iscte.ambco.kmemoize.api

/**
 * Allows calls to the annotated function to be memoized.
 * A memoized function stores calls in an auxiliary data structure
 * to avoid repeated computation of the same call.
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