package keb.server.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MarkerFactory

// when needing to instantiate a logger for a class, do this:
// private val logger = logger()
inline fun <reified T> T.logger(): Logger =
    if (T::class.isCompanion)
        LoggerFactory.getLogger(T::class.java.enclosingClass)
    else
        LoggerFactory.getLogger(T::class.java)

// to write to metric log do this:
// logger.metric("message")
fun Logger.metric(msg: String) {
    this.info(MarkerFactory.getMarker("METRIC"), msg)
}

// lazy-factories

@JvmName("Debug0")
fun Logger.debug(message: () -> Any) {
    if (isDebugEnabled) debug(message.toString())
}

@JvmName("Debug1")
fun Logger.debug(message: () -> String) {
    if (isDebugEnabled) debug(message())
}

@JvmName("Info0")
fun Logger.info(message: () -> String) {
    this.info(message())
}

@JvmName("Info1")
fun Logger.info(message: () -> Any) {
    info(message.toString())
}

@JvmName("Warn0")
fun Logger.warn(message: () -> String) {
    warn(message())
}

@JvmName("Warn1")
fun Logger.warn(message: () -> Any) {
    warn(message.toString())
}

@JvmName("Error0")
fun Logger.error(message: () -> String) {
    error(message())
}

@JvmName("Error1")
fun Logger.error(message: () -> Any) {
    error(message.toString())
}