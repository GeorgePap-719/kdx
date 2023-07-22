package keb.server.util

/**
 * A global logger to help with debugging non-member functions.
 */
object Debugger {
    private val logger = logger()

    fun debug(message: () -> Any) {
        logger.debug(message)
    }

    fun debug(message: () -> String) {
        logger.debug(message)
    }
}