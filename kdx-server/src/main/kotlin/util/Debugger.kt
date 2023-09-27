package kdx.server.util

/**
 * A global logger to help with debugging non-member functions.
 */
object Debugger {
    private val logger = logger()

    @JvmName("DebuggerDebug0")
    fun debug(message: () -> Any) {
        logger.debug(message)
    }

    @JvmName("DebuggerDebug1")
    fun debug(message: () -> String) {
        logger.debug(message)
    }
}