package com.google.appinventor.components.annotations

/**
 * Annotation to mark Simple functions.
 *
 *
 * Note that the Simple compiler will only recognize Java methods marked
 * with this annotation. All other methods will be ignored.
 *
 */
@kotlin.annotation.Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
annotation class SimpleFunction(
    /**
     * If non-empty, description to use in user-level documentation in place of
     * Javadoc, which is meant for developers.
     */
    val description: String = ""
)
