package keb

/**
 * Helper class to retrieve the assertion status.
 */
private class Debugger

internal val ASSERTIONS_ENABLED = Debugger::class.java.desiredAssertionStatus()

actual fun assert(value: () -> Boolean) {
    if (ASSERTIONS_ENABLED && !value()) throw AssertionError()
}