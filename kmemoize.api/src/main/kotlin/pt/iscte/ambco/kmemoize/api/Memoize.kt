package pt.iscte.ambco.kmemoize.api

/**
 * Allows calls to the annotated function to be memoized.
 * A memoized function stores calls in an auxiliary data structure
 * to avoid repeated computation of the same call.
 *
 * The compiler plugin automatically generates the necessary data
 * structure and memoization code.
 *
 * **Example:**
 * ```
 *  import pt.iscte.ambco.kmemoize.api.Memoize
 *
 *  @Memoize
 *  fun foo(n: Int) {
 *      ...
 *  }
 *  ```
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Memoize()