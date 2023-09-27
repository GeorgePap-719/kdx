package kdx

/**
 * Helper class to retrieve the assertion status.
 */
private class Debugger

internal val ASSERTIONS_ENABLED = Debugger::class.java.desiredAssertionStatus()

actual fun assert(value: () -> Boolean) {
    if (ASSERTIONS_ENABLED && !value()) throw AssertionError()
}

// debugging tools for string representation

internal actual val Any.hexAddress: String
    get() = Integer.toHexString(System.identityHashCode(this))

internal actual val Any.classSimpleName: String get() = this::class.java.simpleName