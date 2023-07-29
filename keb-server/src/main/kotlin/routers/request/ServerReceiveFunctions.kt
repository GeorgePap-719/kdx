package keb.server.routers.request

import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.awaitBody

suspend inline fun <reified T : Any> ServerRequest.awaitAndReceive(): T {
    val body = try {
        awaitBody<T>()
    } catch (e: Throwable) {
        // We catch only exceptions related to `coroutines` bridge await.single()
        // and to serialization exceptions.
        if (e is IllegalArgumentException || e is NoSuchElementException) {
            throw IllegalArgumentException("Body is expected to be type of ${T::class.simpleName}")
        }
        // Anything else we propagate it top top-level handler.
        throw e
    }
    return body
}

fun ServerRequest.pathVariableOrNull(name: String): String? {
    val vars = pathVariables()
    return vars[name]
}